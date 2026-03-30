package xtdb.fhir;

import com.example.service.FHIRImportService;
import com.example.util.JsonUtil;
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

import com.zaxxer.hikari.HikariDataSource;

import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@SpringBootApplication
@EnableScheduling
public class PatientGenerator implements AutoCloseable {
  private static final Logger log = LoggerFactory.getLogger(PatientGenerator.class);

  private static final Logger stdoutLog = LoggerFactory.getLogger("stdout");

  static {
    System.setOut(new PrintStream(new OutputStream() {
      private final StringBuilder buf = new StringBuilder();
      @Override public void write(int b) {
        if (b == '\n') {
          stdoutLog.debug("{}", buf.toString());
          buf.setLength(0);
        } else {
          buf.append((char) b);
        }
      }
    }));

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
    Config.set("exporter.fhir.use_us_core_ig", "false");
  }

  private final ExecutorService generatorExecutor = Executors.newSingleThreadExecutor();
  private final ExecutorService insertionExecutor;
  private final HikariDataSource dataSource;
  private final FHIRImportService importService;
  private final int population;

  public PatientGenerator(HikariDataSource dataSource,
                          @Value("${patient-generator.population:2}") int population) {
    this.dataSource = dataSource;
    this.importService = new FHIRImportService(dataSource);
    this.population = population;

    // We better leave one connection idle for DataSource health checks to always succeed
    if (!(dataSource.getMaximumPoolSize() >= 2)) throw new IllegalStateException("pool size must be >= 2");
    this.insertionExecutor = Executors.newFixedThreadPool(dataSource.getMaximumPoolSize() - 1);
  }

  @Override
  public void close() throws Exception {
    generatorExecutor.shutdown();
    insertionExecutor.shutdown();
    if (!generatorExecutor.awaitTermination(5, TimeUnit.SECONDS))
      generatorExecutor.shutdownNow();
    if (!insertionExecutor.awaitTermination(30, TimeUnit.SECONDS))
      insertionExecutor.shutdownNow();
  }

  @Scheduled(fixedDelayString = "#{${patient-generator.interval-seconds} * 1000}")
  @SuppressWarnings("unused")
  public void feedPersonRecord() throws Exception {
    var options = new Generator.GeneratorOptions();
    options.population = population;

    // Important! We rely on population count below for exhausting the exportQueue,
    // therefore population needs to include both dead and alive persons:
    options.overflow = false;

    var ero = new Exporter.ExporterRuntimeOptions();
    ero.enableQueue(Exporter.SupportedFhirVersion.R4);

    var generator = new Generator(options, ero);
    log.debug("Running patient generator...");
    var generatorTask = generatorExecutor.submit(generator::run);

    var insertionFutures = new ArrayList<CompletableFuture<Void>>(options.population);

    for (int recordCount = 0; recordCount < options.population; recordCount++) {
      final int patientIndex = recordCount;
      try {
        log.debug("Obtaining next patient data {}...", patientIndex);
        String patientBundle = ero.getNextRecord(); // blocks until next record available
        var patientBundleNode = JsonUtil.getMapper().readTree(patientBundle);

        var future = CompletableFuture.runAsync(() -> {
          try (var conn = dataSource.getConnection()) {
            importService.processBundle(patientBundleNode, conn);
            log.debug("Done inserting patient {}", patientIndex);
          } catch (Exception e) {
            log.error("Error inserting patient {}", patientIndex, e);
          }
        }, insertionExecutor);

        insertionFutures.add(future);
      } catch (Exception e) {
        // Catch all exceptions for exhausting the record queue - otherwise we will leak generators.
        log.error("Error obtaining patient data {}", patientIndex, e);
      }
    }

    CompletableFuture.allOf(insertionFutures.toArray(new CompletableFuture[0])).join();

    // Rethrow any exceptions thrown by the generator task
    generatorTask.get(10, TimeUnit.SECONDS);
  }

  public static void main(String[] args) {
    SpringApplication app = new SpringApplication(PatientGenerator.class);
    app.setBannerMode(Banner.Mode.OFF);
    app.run(args);
  }
}
