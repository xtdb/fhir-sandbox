package bench;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Produces TOTAL_MSGS patient JSON docs (canned Synthea template, fresh UUID _id
 * per message, keyed by _id) to the Kafka source topic flat-out, then polls
 * SELECT COUNT(*) FROM patient on the attached kafka_src database until all of
 * them are visible. Reports produce time, drain time and indexed rows/sec.
 */
public final class Bench {

    public static void main(String[] args) throws Exception {
        System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "warn");

        String bootstrap   = env("KAFKA_BOOTSTRAP", "localhost:9092");
        String topic       = env("TOPIC", "xtdb-source-events");
        int    totalMsgs   = Integer.parseInt(env("TOTAL_MSGS", "500000"));
        String host        = env("XTDB_HOST", "localhost");
        int    port        = Integer.parseInt(env("XTDB_PORT", "5432"));
        String db          = env("XTDB_DB", "kafka_src");
        String user        = env("XTDB_USER", "xtdb");
        long   timeoutSecs = Long.parseLong(env("DRAIN_TIMEOUT_SECS", "3600"));

        String template = readTemplate();

        System.out.printf("=== bench-kafka-source ===%n");
        System.out.printf("kafka=%s topic=%s total_msgs=%d msg_bytes=%d%n",
            bootstrap, topic, totalMsgs, template.getBytes(StandardCharsets.UTF_8).length);
        System.out.printf("xtdb=jdbc:postgresql://%s:%d/%s%n", host, port, db);

        Properties connProps = new Properties();
        connProps.setProperty("user", user);

        try (Connection conn = DriverManager.getConnection(
                String.format("jdbc:postgresql://%s:%d/%s", host, port, db), connProps)) {
            long baseline = countPatients(conn);
            System.out.printf("baseline patient count: %d%n", baseline);

            Properties props = new Properties();
            props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
            props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
            props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
            props.put(ProducerConfig.ACKS_CONFIG, "all");

            AtomicLong sendErrors = new AtomicLong();
            AtomicReference<Exception> firstError = new AtomicReference<>();

            long produceStart = System.nanoTime();
            try (KafkaProducer<String, String> producer = new KafkaProducer<>(props)) {
                for (int i = 0; i < totalMsgs; i++) {
                    String id = UUID.randomUUID().toString();
                    producer.send(new ProducerRecord<>(topic, id, template.replace("__ID__", id)), (meta, e) -> {
                        if (e != null) {
                            sendErrors.incrementAndGet();
                            firstError.compareAndSet(null, e);
                        }
                    });
                }
                producer.flush();
            }
            long produceEnd = System.nanoTime();
            double produceSecs = (produceEnd - produceStart) / 1e9;

            if (sendErrors.get() > 0) {
                System.out.printf("FAILED: %d/%d sends failed, first error:%n", sendErrors.get(), totalMsgs);
                firstError.get().printStackTrace(System.out);
                System.exit(1);
            }
            System.out.printf("produced %d msgs in %.3f s (%.1f msgs/sec), polling until indexed...%n",
                totalMsgs, produceSecs, totalMsgs / produceSecs);

            long target = baseline + totalMsgs;
            long deadline = produceEnd + timeoutSecs * 1_000_000_000L;
            long lastLog = produceEnd;
            long lastCount = baseline;
            long count;
            while ((count = countPatients(conn)) < target) {
                long now = System.nanoTime();
                if (now > deadline) {
                    System.out.printf("TIMED OUT after %d s: %d/%d rows visible%n",
                        timeoutSecs, count - baseline, totalMsgs);
                    System.exit(2);
                }
                if (now - lastLog >= 10_000_000_000L) {
                    System.out.printf("indexed %d/%d (%.1f rows/sec)%n",
                        count - baseline, totalMsgs, (count - lastCount) / ((now - lastLog) / 1e9));
                    lastLog = now;
                    lastCount = count;
                }
                Thread.sleep(1000);
            }
            long allVisible = System.nanoTime();

            double drainSecs = (allVisible - produceEnd) / 1e9;
            double totalSecs = (allVisible - produceStart) / 1e9;

            System.out.println();
            System.out.println("=== results ===");
            System.out.printf("messages           : %d%n", totalMsgs);
            System.out.printf("produce seconds    : %.3f (%.1f msgs/sec)%n", produceSecs, totalMsgs / produceSecs);
            System.out.printf("drain seconds      : %.3f (produce done -> all rows visible)%n", drainSecs);
            System.out.printf("end-to-end seconds : %.3f (produce start -> all rows visible)%n", totalSecs);
            System.out.printf("indexed rows/sec   : %.1f%n", totalMsgs / totalSecs);
        }
    }

    // The patient table only exists once the kafka_src source has indexed its
    // first message, so treat "table not found" as a count of 0.
    private static long countPatients(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) c FROM patient")) {
            rs.next();
            return rs.getLong(1);
        } catch (SQLException e) {
            if (e.getMessage() != null && e.getMessage().toLowerCase().contains("table")) return 0L;
            throw e;
        }
    }

    private static String readTemplate() throws Exception {
        try (InputStream in = Bench.class.getResourceAsStream("/patient-template.json")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
        }
    }

    private static String env(String key, String def) {
        String v = System.getenv(key);
        return (v == null || v.isEmpty()) ? def : v;
    }

    private Bench() {}
}
