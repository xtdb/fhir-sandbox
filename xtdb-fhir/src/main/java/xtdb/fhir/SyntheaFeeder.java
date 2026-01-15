package xtdb.fhir;

import com.example.config.DatabaseConfig;
import com.example.service.FHIRImportService;
import com.example.util.JsonUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.mitre.synthea.engine.Generator;
import org.mitre.synthea.export.Exporter;
import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.world.agents.Person;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SpringBootApplication
@EnableScheduling
public class SyntheaFeeder {
  private static final Logger log = LoggerFactory.getLogger(SyntheaFeeder.class);

  @Autowired
  private SyntheaFeederConf conf;

  private final Exporter.ExporterRuntimeOptions ero;
  private final DatabaseConfig dbConf = new DatabaseConfig(new Properties());
  private final FHIRImportService importService = new FHIRImportService(dbConf);

  public SyntheaFeeder() {
    Config.set("exporter.fhir.export", "false");
    Config.set("exporter.hospital.fhir.export", "false");
    Config.set("exporter.practitioner.fhir.export", "false");
    Config.set("generate.only_dead_patients", "false");

    Generator.GeneratorOptions options = new Generator.GeneratorOptions();
    options.population = 1000;

    ero = new Exporter.ExporterRuntimeOptions();
    ero.enableQueue(Exporter.SupportedFhirVersion.R4);

    // Create and start generator
    Generator generator = new Generator(options, ero);
    ExecutorService generatorService = Executors.newFixedThreadPool(1);
    generatorService.submit(() -> generator.run());
  }

  @Scheduled(fixedRateString = "#{@syntheaFeederConf.intervalSeconds * 1000}")
  public void feedPersonRecord() {
    try {
      log.info("obtaining person data...");
      var personNode = JsonUtil.getMapper().readTree(ero.getNextRecord());

      log.info("inserting into XTDB...");
      try (var conn = dbConf.getConnection()) {
        importService.processBundle(personNode, conn);
      }
      log.info("done");
    } catch (InterruptedException | JsonProcessingException | SQLException e) {
      throw new RuntimeException(e);
    }
  }

  public static void main(String[] args) {
    SpringApplication app = new SpringApplication(SyntheaFeeder.class);
    app.setBannerMode(Banner.Mode.OFF);
    app.run(args);
  }
}
