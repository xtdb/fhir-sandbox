package xtdb.fhir;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Selects the {@link ResourceWriter} from the {@code patient-generator.target}
 * property ({@code xtdb} by default, or {@code postgres}). Set per-deployment via
 * the app ConfigMap so the same image can run against either target.
 */
@Configuration
public class WriterConfig {

  private static final Logger log = LoggerFactory.getLogger(WriterConfig.class);

  @Bean
  public ResourceWriter resourceWriter(@Value("${patient-generator.target:xtdb}") String target) {
    log.info("FHIR write target: {}", target);
    return switch (target.toLowerCase()) {
      case "xtdb" -> new XtdbRecordsWriter();
      case "postgres" -> new PostgresColumnWriter();
      default -> throw new IllegalArgumentException(
          "Unknown patient-generator.target '" + target + "' (expected 'xtdb' or 'postgres')");
    };
  }
}
