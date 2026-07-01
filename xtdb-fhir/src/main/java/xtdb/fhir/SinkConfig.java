package xtdb.fhir;

import com.zaxxer.hikari.HikariDataSource;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Properties;

/**
 * Selects the {@link ResourceSink} from the {@code patient-generator.target}
 * property ({@code xtdb} by default, {@code postgres}, or {@code kafka}). Set
 * per-deployment via the app ConfigMap so the same image can run against any
 * target. The {@code xtdb}/{@code postgres} targets need a JDBC DataSource; the
 * {@code kafka} target needs {@code patient-generator.kafka.*} instead (and its
 * deployment excludes DataSourceAutoConfiguration).
 */
@Configuration
public class SinkConfig {

  private static final Logger log = LoggerFactory.getLogger(SinkConfig.class);

  @Bean
  public ResourceSink resourceSink(
      @Value("${patient-generator.target:xtdb}") String target,
      ObjectProvider<HikariDataSource> dataSourceProvider,
      @Value("${patient-generator.kafka.bootstrap-servers:}") String bootstrapServers,
      @Value("${patient-generator.kafka.topic:}") String topic,
      @Value("${patient-generator.kafka.concurrency:4}") int kafkaConcurrency) {
    log.info("FHIR write target: {}", target);

    return switch (target.toLowerCase()) {
      case "xtdb" -> jdbcSink(dataSourceProvider, new XtdbRecordsWriter());
      case "postgres" -> jdbcSink(dataSourceProvider, new PostgresColumnWriter());
      case "kafka" -> kafkaSink(bootstrapServers, topic, kafkaConcurrency);
      default -> throw new IllegalArgumentException(
          "Unknown patient-generator.target '" + target + "' (expected 'xtdb', 'postgres' or 'kafka')");
    };
  }

  private ResourceSink jdbcSink(ObjectProvider<HikariDataSource> dataSourceProvider, ResourceWriter writer) {
    HikariDataSource dataSource = dataSourceProvider.getObject();
    // Leave one connection idle so DataSource health checks always succeed.
    if (dataSource.getMaximumPoolSize() < 2)
      throw new IllegalStateException("pool size must be >= 2");
    return new JdbcResourceSink(dataSource, writer, dataSource.getMaximumPoolSize() - 1);
  }

  private ResourceSink kafkaSink(String bootstrapServers, String topic, int concurrency) {
    if (bootstrapServers.isBlank() || topic.isBlank())
      throw new IllegalArgumentException(
          "patient-generator.kafka.bootstrap-servers and .topic are required for target=kafka");

    var props = new Properties();
    props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
    props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
    props.put(ProducerConfig.ACKS_CONFIG, "all");
    return new KafkaResourceSink(new KafkaProducer<>(props), topic, concurrency);
  }
}
