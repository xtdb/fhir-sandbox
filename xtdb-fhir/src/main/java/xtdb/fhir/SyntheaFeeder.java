package xtdb.fhir;

import com.example.service.FHIRImportService;
import com.example.util.JsonUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.mitre.synthea.engine.Generator;
import org.mitre.synthea.export.Exporter;
import org.mitre.synthea.helpers.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import javax.sql.DataSource;
import java.sql.SQLException;

@SpringBootApplication
@EnableScheduling
public class SyntheaFeeder {
  private static final Logger log = LoggerFactory.getLogger(SyntheaFeeder.class);

  // Static initializer - set Synthea config BEFORE any Synthea classes initialize
  static {
    // Disable ALL file exports (we only use the in-memory queue)
    Config.set("exporter.fhir.export", "false");
    Config.set("exporter.hospital.fhir.export", "false");
    Config.set("exporter.practitioner.fhir.export", "false");
    Config.set("exporter.metadata.export", "false");
    Config.set("exporter.clinical_note.export", "false");
    Config.set("exporter.ccda.export", "false");
    Config.set("exporter.csv.export", "false");
    Config.set("exporter.text.export", "false");
    Config.set("exporter.symptoms.csv.export", "false");
    Config.set("generate.only_dead_patients", "false");
    Config.set("exporter.fhir.use_us_core_ig", "false");
  }

  private final DataSource dataSource;
  private final FHIRImportService importService;
  private final int population;

  public SyntheaFeeder(DataSource dataSource,
                       @Value("${synthea-feeder.population:2}") int population) {
    this.dataSource = dataSource;
    this.importService = new FHIRImportService(dataSource);
    this.population = population;
  }

  @Scheduled(fixedDelayString = "#{${synthea-feeder.interval-seconds} * 1000}")
  @SuppressWarnings("unused")
  public void feedPersonRecord() throws InterruptedException {
    log.debug("new GeneratorOptions...");
    var options = new Generator.GeneratorOptions();

    options.population = population;

    log.debug("new ExporterRuntimeOptions...");
    var ero = new Exporter.ExporterRuntimeOptions();
    ero.enableQueue(Exporter.SupportedFhirVersion.R4);

    log.debug("new Generator...");
    var generator = new Generator(options, ero);
    log.debug("starting generator.run thread...");
    new Thread(() -> {
      generator.run();
      log.debug("generator.run done");
    }).start();

    for (int recordCount = 0; recordCount < options.population; recordCount++) {
      log.debug("next patient data {} {}...", recordCount, ero.isRecordQueueEmpty());
      insertRecord(ero.getNextRecord());
    }
  }

  private void insertRecord(String jsonRecord) {
    try {
      var personNode = JsonUtil.getMapper().readTree(jsonRecord);

      log.debug("inserting into XTDB...");
      try (var conn = dataSource.getConnection()) {
        importService.processBundle(personNode, conn);
      }

      log.debug("done");
    } catch (JsonProcessingException | SQLException e) {
      throw new RuntimeException(e);
    }
  }

  public static void main(String[] args) {
    SpringApplication app = new SpringApplication(SyntheaFeeder.class);
    app.setBannerMode(Banner.Mode.OFF);
    app.run(args);
  }
}
