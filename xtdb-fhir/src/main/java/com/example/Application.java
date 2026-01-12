package com.example;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.config.DatabaseConfig;
import com.example.service.FHIRImportService;

/**
 * Main entry point for the FHIR to XTDB importer.
 *
 * Usage: Compile with 'mvn clean package -DskipTests'
 * Then: java -jar target/xtdb-fhir-1.0-SNAPSHOT.jar <input-directory>
 *
 * Currently supports batch import from a directory when specified as a command line argument.
 * Future: Possible Kafka streaming integration will use FHIRImportService directly?
 */
public class Application {

  private static final Logger logger = LoggerFactory.getLogger(Application.class);

  public static void main(String[] args) {
    logger.info("================================================");
    logger.info("   FHIR to XTDB Importer");
    logger.info("================================================");

    // Require CLI argument for directory import
    if (args.length == 0) {
      logger.info("No input directory specified.");
      logger.info("Usage: java -jar target/xtdb-fhir-1.0-SNAPSHOT.jar <input-directory>");
      return;
    }

    Path inputPath = Paths.get(args[0]);
    logger.info("Input directory: {}", inputPath.toAbsolutePath());

    // Validate input directory exists
    if (!Files.isDirectory(inputPath)) {
      logger.error("Input directory does not exist: {}", inputPath.toAbsolutePath());
      System.exit(1);
    }

    // Use try-with-resources to ensure database connections are closed
    try (DatabaseConfig dbConfig = new DatabaseConfig()) {

      // Create service with injected database config
      FHIRImportService importService = new FHIRImportService(dbConfig);

      // Run the import
      importService.importDirectory(inputPath);

      // Check for errors
      if (importService.getErrors() > 0) {
        logger.warn("Import completed with {} errors", importService.getErrors());
        System.exit(1);
      }

    } catch (Exception e) {
      logger.error("Import failed: {}", e.getMessage(), e);
      System.exit(1);
    }

    logger.info("Application finished successfully");
  }
}
