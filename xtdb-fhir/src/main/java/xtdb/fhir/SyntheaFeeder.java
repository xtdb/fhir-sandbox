package xtdb.fhir;

import org.mitre.synthea.engine.Generator;
import org.mitre.synthea.export.Exporter;
import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.world.agents.Person;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SpringBootApplication
@EnableScheduling
public class SyntheaFeeder {
  @Autowired
  private SyntheaFeederConf conf;

  private final Exporter.ExporterRuntimeOptions ero;

  public SyntheaFeeder() {
    Config.set("exporter.fhir.export", "false");
    Config.set("exporter.hospital.fhir.export", "false");
    Config.set("exporter.practitioner.fhir.export", "false");
    Config.set("generate.only_dead_patients", "false");

    Generator.GeneratorOptions options = new Generator.GeneratorOptions();
    options.population = Integer.MAX_VALUE;

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
      System.out.println("generating person...");
      System.out.println(ero.getNextRecord());
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }

  public static void main(String[] args) {
    SpringApplication app = new SpringApplication(SyntheaFeeder.class);
    app.setBannerMode(Banner.Mode.OFF);
    app.run(args);
  }
}
