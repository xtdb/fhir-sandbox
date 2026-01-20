package xtdb.fhir;

import com.example.service.FHIRImportService;
import com.example.util.JsonUtil;
import com.fasterxml.jackson.databind.JsonNode;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@SpringBootApplication
@EnableScheduling
public class SyntheaFeeder implements AutoCloseable {
  private static final Logger log = LoggerFactory.getLogger(SyntheaFeeder.class);

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

  private final ExecutorService generatorExecutor = Executors.newSingleThreadExecutor();
  private final DataSource dataSource;
  private final FHIRImportService importService;
  private final int population;

  public SyntheaFeeder(DataSource dataSource,
                       @Value("${synthea-feeder.population:2}") int population) {
    this.dataSource = dataSource;
    this.importService = new FHIRImportService(dataSource);
    this.population = population;
  }

  @Override
  public void close() throws Exception {
    generatorExecutor.shutdown();
    if (!generatorExecutor.awaitTermination(5, TimeUnit.SECONDS))
      generatorExecutor.shutdownNow();
  }

  @Scheduled(fixedDelayString = "#{${synthea-feeder.interval-seconds} * 1000}")
  @SuppressWarnings("unused")
  public void feedPersonRecord() {
    log.debug("new GeneratorOptions...");
    var options = new Generator.GeneratorOptions();

    options.population = population;

    log.debug("new ExporterRuntimeOptions...");
    var ero = new Exporter.ExporterRuntimeOptions();
    ero.enableQueue(Exporter.SupportedFhirVersion.R4);

    log.debug("new Generator...");
    var generator = new Generator(options, ero);
    log.debug("submitting generator.run task...");
    var generatorTask = generatorExecutor.submit(() -> {
      log.debug("starting generator.run done...");
      generator.run();
      log.debug("generator.run done");
    });

    for (int recordCount = 0; recordCount < options.population; recordCount++) {
      try {
        log.debug("obtaining next patient data {}...", recordCount);
        String patientBundle = ero.getNextRecord();
        var patientBundleNode = JsonUtil.getMapper().readTree(patientBundle);

        if (log.isDebugEnabled()) {
          log.debug("inserting into XTDB patient {}...", tryGetPatientFamilyName(patientBundleNode));
        }

        try (var conn = dataSource.getConnection()) {
          importService.processBundle(patientBundleNode, conn);
        }

        log.debug("done inserting patient {}", recordCount);
      } catch (Throwable e) {
        // Catch all exceptions for exhausting the record queue - otherwise we will leak generators.
        log.error("Error inserting patient", e);
      }
    }

    // Generator sometimes gets blocked when having generated multiple dead patients (why?)
    // We need to interrupt it for it to finalize.
    if (generatorTask.cancel(true))
      log.warn("generator.run had to be cancelled!");
  }

  private String tryGetPatientFamilyName(JsonNode patientBundleNode) {
    try {
      return patientBundleNode.get("entry").get(0).get("resource").get("name").get(0).get("family").toString();
    } catch (Exception e) {
      return null;
    }
  }

  public static void main(String[] args) {
    SpringApplication app = new SpringApplication(SyntheaFeeder.class);
    app.setBannerMode(Banner.Mode.OFF);
    app.run(args);
  }

}
