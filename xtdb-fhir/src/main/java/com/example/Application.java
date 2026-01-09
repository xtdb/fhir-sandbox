package com.example;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.config.DatabaseConfig;
import com.example.service.FHIRImportService;

/**
 * Main entry point for the FHIR to XTDB importer.
 *
 * All business logic lives in FHIRImportService.
 */
public class Application {

  private static final Logger logger = LoggerFactory.getLogger(Application.class);

  public static void main(String[] args) {
    logger.info("================================================");
    logger.info("   FHIR to XTDB Importer");
    logger.info("================================================");

    // Determine input directory from args or properties
    Path inputPath = getInputDirectory(args);

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

  // Determine input directory from command-line args or application.properties
  private static Path getInputDirectory(String[] args) {
    // Command line argument takes precedence
    if (args.length > 0) {
      return Paths.get(args[0]);
    }

    // Otherwise use application.properties
    Properties props = new Properties();
    try (InputStream is = Application.class.getClassLoader()
            .getResourceAsStream("application.properties")) {
      if (is != null) {
        props.load(is);
      }
    } catch (IOException e) {
      logger.debug("Could not load application.properties: {}", e.getMessage());
    }

    String inputDir = props.getProperty("import.input.directory", "data/fhir");
    return Paths.get(inputDir);
  }
}

