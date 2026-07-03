package bench;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;
import java.util.UUID;

/**
 * Inserts TOTAL_ROWS patient rows into Postgres core.patient (the CDC
 * publication source) in batches of ROWS_PER_TXN, one transaction per batch,
 * then polls SELECT COUNT(*) FROM core.patient on the attached pg_cdc database
 * until all of them have replicated into XTDB. Reports insert time, drain time
 * and replicated rows/sec.
 */
public final class Bench {

    public static void main(String[] args) throws Exception {
        String pgHost      = env("PG_HOST", "localhost");
        int    pgPort      = Integer.parseInt(env("PG_PORT", "5432"));
        String pgDb        = env("PG_DB", "cdc");
        String pgUser      = env("PG_USER", "postgres");
        String pgPassword  = env("PG_PASSWORD", "");
        int    totalRows   = Integer.parseInt(env("TOTAL_ROWS", "500000"));
        int    rowsPerTxn  = Integer.parseInt(env("ROWS_PER_TXN", "1000"));
        String host        = env("XTDB_HOST", "localhost");
        int    port        = Integer.parseInt(env("XTDB_PORT", "5432"));
        String db          = env("XTDB_DB", "pg_cdc");
        String user        = env("XTDB_USER", "xtdb");
        long   timeoutSecs = Long.parseLong(env("DRAIN_TIMEOUT_SECS", "3600"));

        System.out.printf("=== bench-cdc-source ===%n");
        System.out.printf("pg=jdbc:postgresql://%s:%d/%s total_rows=%d rows_per_txn=%d%n",
            pgHost, pgPort, pgDb, totalRows, rowsPerTxn);
        System.out.printf("xtdb=jdbc:postgresql://%s:%d/%s%n", host, port, db);

        Properties pgProps = new Properties();
        pgProps.setProperty("user", pgUser);
        if (!pgPassword.isEmpty()) pgProps.setProperty("password", pgPassword);

        Properties xtdbProps = new Properties();
        xtdbProps.setProperty("user", user);

        try (Connection pg = DriverManager.getConnection(
                 String.format("jdbc:postgresql://%s:%d/%s", pgHost, pgPort, pgDb), pgProps);
             Connection xtdb = DriverManager.getConnection(
                 String.format("jdbc:postgresql://%s:%d/%s", host, port, db), xtdbProps)) {
            long baseline = countPatients(xtdb);
            System.out.printf("baseline pg_cdc patient count: %d%n", baseline);

            // Same row shape the generator's PostgresColumnWriter produces for
            // patients; status marks the rows as bench-inserted for cleanup.
            String sql = "INSERT INTO core.patient (_id, resource_type, status, last_updated) "
                + "VALUES (?, 'patient', 'bench', now())";

            pg.setAutoCommit(false);
            long insertStart = System.nanoTime();
            try (PreparedStatement ps = pg.prepareStatement(sql)) {
                for (int inserted = 0; inserted < totalRows; ) {
                    int batch = Math.min(rowsPerTxn, totalRows - inserted);
                    for (int i = 0; i < batch; i++) {
                        ps.setString(1, UUID.randomUUID().toString());
                        ps.addBatch();
                    }
                    ps.executeBatch();
                    pg.commit();
                    inserted += batch;
                }
            }
            long insertEnd = System.nanoTime();
            double insertSecs = (insertEnd - insertStart) / 1e9;
            System.out.printf("inserted %d rows in %.3f s (%.1f rows/sec), polling until replicated...%n",
                totalRows, insertSecs, totalRows / insertSecs);

            long target = baseline + totalRows;
            long deadline = insertEnd + timeoutSecs * 1_000_000_000L;
            long lastLog = insertEnd;
            long lastCount = baseline;
            long count;
            while ((count = countPatients(xtdb)) < target) {
                long now = System.nanoTime();
                if (now > deadline) {
                    System.out.printf("TIMED OUT after %d s: %d/%d rows visible%n",
                        timeoutSecs, count - baseline, totalRows);
                    System.exit(2);
                }
                if (now - lastLog >= 10_000_000_000L) {
                    System.out.printf("replicated %d/%d (%.1f rows/sec)%n",
                        count - baseline, totalRows, (count - lastCount) / ((now - lastLog) / 1e9));
                    lastLog = now;
                    lastCount = count;
                }
                Thread.sleep(1000);
            }
            long allVisible = System.nanoTime();

            double drainSecs = (allVisible - insertEnd) / 1e9;
            double totalSecs = (allVisible - insertStart) / 1e9;

            System.out.println();
            System.out.println("=== results ===");
            System.out.printf("rows                : %d%n", totalRows);
            System.out.printf("insert seconds      : %.3f (%.1f rows/sec)%n", insertSecs, totalRows / insertSecs);
            System.out.printf("drain seconds       : %.3f (insert done -> all rows visible)%n", drainSecs);
            System.out.printf("end-to-end seconds  : %.3f (insert start -> all rows visible)%n", totalSecs);
            System.out.printf("replicated rows/sec : %.1f%n", totalRows / totalSecs);
        }
    }

    // The pg_cdc table only exists once the source has snapshotted/replicated,
    // so treat "table not found" as a count of 0.
    private static long countPatients(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) c FROM core.patient")) {
            rs.next();
            return rs.getLong(1);
        } catch (SQLException e) {
            if (e.getMessage() != null && e.getMessage().toLowerCase().contains("table")) return 0L;
            throw e;
        }
    }

    private static String env(String key, String def) {
        String v = System.getenv(key);
        return (v == null || v.isEmpty()) ? def : v;
    }

    private Bench() {}
}
