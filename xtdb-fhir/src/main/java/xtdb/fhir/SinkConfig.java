package xtdb.fhir;

import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Selects the {@link ResourceSink} from the {@code patient-generator.target}
 * property ({@code xtdb} by default, or {@code postgres}). Set per-deployment via
 * the app ConfigMap so the same image can run against either target.
 */
@Configuration
public class SinkConfig {

  private static final Logger log = LoggerFactory.getLogger(SinkConfig.class);

  @Bean
  public ResourceSink resourceSink(@Value("${patient-generator.target:xtdb}") String target,
                                   HikariDataSource dataSource) {
    log.info("FHIR write target: {}", target);

    // Leave one connection idle so DataSource health checks always succeed.
    if (dataSource.getMaximumPoolSize() < 2)
      throw new IllegalStateException("pool size must be >= 2");
    int concurrency = dataSource.getMaximumPoolSize() - 1;

    ResourceWriter writer = switch (target.toLowerCase()) {
      case "xtdb" -> new XtdbRecordsWriter();
      case "postgres" -> new PostgresColumnWriter();
      default -> throw new IllegalArgumentException(
          "Unknown patient-generator.target '" + target + "' (expected 'xtdb' or 'postgres')");
    };
    return new JdbcResourceSink(dataSource, writer, concurrency);
  }
}
