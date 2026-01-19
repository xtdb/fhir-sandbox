package com.example.service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Stream;

import ca.uhn.fhir.model.api.TemporalPrecisionEnum;
import com.fasterxml.jackson.databind.node.*;
import org.hl7.fhir.r4.model.DateTimeType;
import org.postgresql.util.PGobject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.util.JsonUtil;
import com.fasterxml.jackson.databind.JsonNode;

import javax.sql.DataSource;

// Service that imports FHIR JSON bundles into XTDB.
public class FHIRImportService {

  private static final Logger logger = LoggerFactory.getLogger(FHIRImportService.class);

  private final DataSource dataSource;

  // Statistics for logging
  private int filesProcessed = 0;
  private int patientsInserted = 0;
  private int encountersInserted = 0;
  private int conditionsInserted = 0;
  private int otherResourcesStored = 0;
  private int errors = 0;

  public FHIRImportService(DataSource dataSource) {
    this.dataSource = dataSource;
  }

  // =========================================================================
  // IMPORT METHODS
  // =========================================================================

  /**
   * Import all FHIR JSON files from a directory.
   * 
   * @param inputDirectory The directory containing FHIR JSON files to import
   */
  public void importDirectory(Path inputDirectory) {
    logger.info("Starting import from: {}", inputDirectory.toAbsolutePath());

    if (!Files.isDirectory(inputDirectory)) {
      throw new IllegalArgumentException("Not a directory: " + inputDirectory);
    }

    long startTime = System.currentTimeMillis();

    try (Stream<Path> files = Files.list(inputDirectory)) {
      files.filter(p -> p.toString().endsWith(".json"))
           .forEach(this::importFile);
    } catch (IOException e) {
      throw new RuntimeException("Failed to list directory: " + inputDirectory, e);
    }

    long duration = System.currentTimeMillis() - startTime;
    logSummary(duration);
  }

  /**
   * Import a single FHIR Bundle file.
   * 
   * @param filePath The path to the specific FHIR Bundle file to import
   */
  public void importFile(Path filePath) {
    File file = filePath.toFile();
    logger.debug("Processing: {}", file.getName());

    try {
      JsonNode root = JsonUtil.parseFile(file);

      String resourceType = JsonUtil.getText(root, "resourceType");
      if (!"Bundle".equals(resourceType)) {
        logger.warn("Skipping {} - not a Bundle (found: {})", file.getName(), resourceType);
        return;
      }

      try (Connection conn = dataSource.getConnection()) {
        conn.setAutoCommit(false);
        processBundle(root, conn);
        conn.commit();
        filesProcessed++;
      } catch (SQLException e) {
        logger.error("Database error processing {}: {}", file.getName(), e.getMessage());
        errors++;
      }

    } catch (IOException e) {
      logger.error("Failed to read {}: {}", file.getName(), e.getMessage());
      errors++;
    }
  }

  // =========================================================================
  // BUNDLE PROCESSING
  // =========================================================================

  /**
   * Process all entries in a FHIR Bundle.
   *
   * Collects resources by type and inserts in batches for better performance.
   * Other resource types (not Patient/Encounter/Condition) are bundled into
   * the patient record's "data" field for future use.
   *
   * @param bundle The FHIR Bundle to process
   * @param conn The database connection to use for insertion
   * @throws SQLException If there is an error processing the bundle
   */
  public void processBundle(JsonNode bundle, Connection conn) throws SQLException {
    var resourcesByType = extractResourcesByType(bundle);

    for (var mapEntry : resourcesByType.entrySet()) {
      var resourceType = mapEntry.getKey();
      var resources = mapEntry.getValue();
      insertBatch(conn, String.format("INSERT INTO %s RECORDS ?", resourceType), resources);
      logger.info("inserted {} resources of type {}", resources.size(), resourceType);
    }
  }

  public HashMap<String, List<JsonNode>> extractResourcesByType(JsonNode bundle) {
    var resourcesByType = new HashMap<String, List<JsonNode>>();

    for (JsonNode entry : bundle.get("entry")) {
      var resource = (ObjectNode) entry.get("resource");
      var resourceType = JsonUtil.getText(resource, "resourceType");

      setReferenceAsId(resource, "subject");
      setReferenceAsId(resource, "patient");

      JsonUtil.convertValues(resource, v -> {
        var d = toJsonLdDateOrNull(v);
        return d == null ? v : d;
      });

      JsonUtil.convertKeysToSnakeCase(resource);
      resource.set("_id", resource.get("id")); // must be done after snake_case is applied to keys
      resource.remove("id");
      resourcesByType.computeIfAbsent(JsonUtil.toSnakeCase(resourceType), k -> new ArrayList<>()).add(resource);
    }

    // Second pass: building the records, attaching other resources to just the patients table
//    List<ObjectNode> patients = new ArrayList<>();
//    List<ObjectNode> encounters = new ArrayList<>();
//    List<ObjectNode> conditions = new ArrayList<>();
//
//    for (JsonNode resource : patientResources) {
//      try {
//        String patientId = JsonUtil.getText(resource, "id");
//        List<JsonNode> allOtherResources = otherResourcesByPatient.get(patientId);
//        patients.add(buildPatientRecord(resource, allOtherResources));
//        if (allOtherResources != null) {
//          otherResourcesStored += allOtherResources.size();
//        }
//      } catch (Exception e) {
//        logger.warn("Failed to build Patient record: {}", e.getMessage());
//      }
//    }
//
//    for (JsonNode resource : encounterResources) {
//      try {
//        encounters.add(buildEncounterRecord(resource));
//      } catch (Exception e) {
//        logger.warn("Failed to build Encounter record: {}", e.getMessage());
//      }
//    }
//
//    for (JsonNode resource : conditionResources) {
//      try {
//        conditions.add(buildConditionRecord(resource));
//      } catch (Exception e) {
//        logger.warn("Failed to build Condition record: {}", e.getMessage());
//      }
//    }
    return resourcesByType;
  }

  private static ObjectNode toJsonLdDateOrNull(ValueNode v) {
    if (v.isTextual()) {
      try {
        var asFhirDate = new DateTimeType(v.textValue());
        if (asFhirDate.getPrecision() == TemporalPrecisionEnum.MONTH
            || asFhirDate.getPrecision() == TemporalPrecisionEnum.DAY) {
          var dateJsonLd = JsonNodeFactory.instance.objectNode();
          dateJsonLd.set("@type", new TextNode("xt:date"));
          dateJsonLd.set("@value", new TextNode(v.textValue()));
          return dateJsonLd;
        } else if (asFhirDate.getPrecision().ordinal() >= TemporalPrecisionEnum.MINUTE.ordinal()){
          var dateJsonLd = JsonNodeFactory.instance.objectNode();
          dateJsonLd.set("@type", new TextNode("xt:timestamptz"));
          dateJsonLd.set("@value", new TextNode(v.textValue()));
          return dateJsonLd;
        }
      } catch (ca.uhn.fhir.parser.DataFormatException ignored) {}
    }
    return null;
  }

  private void setReferenceAsId(ObjectNode resource, String topProperty) {
    var subjectRef = JsonUtil.getText(resource, topProperty, "reference");
    if (subjectRef != null) {
      resource.set(topProperty + "_id", new TextNode(extractIdFromReference(subjectRef)));
    }
  }

  /**
   * Insert a batch of records using XTDB RECORDS syntax.
   * Uses PGobject with "json" type for efficient JSON transfer.
   *
   * @param conn The database connection
   * @param sql The SQL statement for insertion
   * @param records The list of records to insert
   * @throws SQLException If there is a database error
   */
  private void insertBatch(Connection conn, String sql, List<JsonNode> records)
      throws SQLException {
    if (records.isEmpty()) return;

    try (PreparedStatement ps = conn.prepareStatement(sql)) {
      for (var record : records) {
        PGobject jsonObject = new PGobject();
        jsonObject.setType("json");
        jsonObject.setValue(record.toString());

        ps.setObject(1, jsonObject);
        ps.addBatch();
      }
      ps.executeBatch();
    }
  }

  // =========================================================================
  // RECORD BUILDERS
  // =========================================================================

  /**
   * Build a Patient record for XTDB insertion.
   *
   * Structure:
   * {
   *   "_id": "...",
   *   "name": "John Smith",
   *   "gender": "male",
   *   "birth_date": "1985-03-15",
   *   "patient_data": { ...full FHIR Patient resource... },
   *   "data": [ ...other FHIR resources for this patient... ]
   * }
   *
   * @param resource The FHIR Patient resource
   * @param allOtherResources Other FHIR resources associated with this patient (may be null)
   */
  ObjectNode buildPatientRecord(JsonNode resource, List<JsonNode> allOtherResources) {
    ObjectNode record = JsonUtil.getMapper().createObjectNode();

    record.put("_id", JsonUtil.getText(resource, "id"));
    record.put("name", extractPatientName(resource));
    record.put("gender", JsonUtil.getText(resource, "gender"));

    // XTDB handles date strings in ISO format
    String birthDate = JsonUtil.getText(resource, "birthDate");
    if (birthDate != null) {
      record.put("birth_date", birthDate);
    }

    // Store full FHIR Patient resource in patient_data field
    record.set("patient_data", resource);

    // Store all other resources in data field as an array
    if (allOtherResources != null && !allOtherResources.isEmpty()) {
      ArrayNode dataArray = JsonUtil.getMapper().createArrayNode();
      for (JsonNode otherResource : allOtherResources) {
        dataArray.add(otherResource);
      }
      record.set("data", dataArray);
    }

    return record;
  }

  /**
   * Build an Encounter record for XTDB insertion.
   * 
   * Structure:
   * {
   *   "_id": "...",
   *   "patient_id": "...",
   *   "encounter_type": "Outpatient",
   *   "period_start": "2023-01-15T10:00:00Z",
   *   "period_end": "2023-01-15T11:00:00Z",
   *   "_valid_from": "2023-01-15T10:00:00Z",
   *   "_valid_to": "2023-01-15T11:00:00Z",
   *   "encounter_data": { ...full FHIR Encounter resource... }
   * }
   * 
   * Structure includes _valid_from/_valid_to for XTDB temporal queries.
   */
  ObjectNode buildEncounterRecord(JsonNode resource) {
    ObjectNode record = JsonUtil.getMapper().createObjectNode();

    record.put("_id", JsonUtil.getText(resource, "id"));

    String patientRef = JsonUtil.getText(resource, "subject", "reference");
    record.put("patient_id", extractIdFromReference(patientRef));

    record.put("encounter_type", extractEncounterType(resource));

    // Period fields - stored as ISO strings, XTDB parses them
    String periodStart = JsonUtil.getText(resource, "period", "start");
    String periodEnd = JsonUtil.getText(resource, "period", "end");

    if (periodStart != null) {
      record.put("period_start", periodStart);
      record.put("_valid_from", periodStart);  // XTDB temporal bound
    }
    if (periodEnd != null) {
      record.put("period_end", periodEnd);
      // Only set _valid_to if different from _valid_from (XTDB requires valid_to > valid_from)
      if (!periodEnd.equals(periodStart)) {
        record.put("_valid_to", periodEnd);  // XTDB temporal bound
      }
    }

    record.set("encounter_data", resource);

    return record;
  }

  /**
   * Build a Condition record for XTDB insertion.
   * 
   * Structure:
   * {
   *   "_id": "...",
   *   "patient_id": "...",
   *   "encounter_id": "...",
   *   "snomed_code": "123456",
   *   "clinical_status": "active",
   *   "onset_date_time": "2023-01-15T10:00:00Z",
   *   "_valid_from": "2023-01-15T10:00:00Z",
   *   "_valid_to": "2023-06-15T10:00:00Z",
   *   "condition_data": { ...full FHIR Condition resource... }
   * }
   * 
   * Conditions are imo ideal for demonstrating XTDB bitemporality:
   * - _valid_from = onsetDateTime (when condition started)
   * - _valid_to = abatementDateTime (when condition resolved, null if ongoing)
   */
  ObjectNode buildConditionRecord(JsonNode resource) {
    ObjectNode record = JsonUtil.getMapper().createObjectNode();

    record.put("_id", JsonUtil.getText(resource, "id"));

    String patientRef = JsonUtil.getText(resource, "subject", "reference");
    record.put("patient_id", extractIdFromReference(patientRef));

    String encounterRef = JsonUtil.getText(resource, "encounter", "reference");
    if (encounterRef != null) {
      record.put("encounter_id", extractIdFromReference(encounterRef));
    }

    record.put("snomed_code", extractSnomedCode(resource));
    record.put("clinical_status", extractClinicalStatus(resource));

    // Temporal fields for XTDB bitemporal queries
    String onsetDateTime = JsonUtil.getText(resource, "onsetDateTime");
    if (onsetDateTime != null) {
      record.put("onset_date_time", onsetDateTime);
      record.put("_valid_from", onsetDateTime);
    }

    String abatementDateTime = JsonUtil.getText(resource, "abatementDateTime");
    // Only set _valid_to if different from _valid_from (XTDB requires valid_to > valid_from)
    if (abatementDateTime != null && !abatementDateTime.equals(onsetDateTime)) {
      record.put("_valid_to", abatementDateTime);
    }
    // Note: _valid_to is null/absent for ongoing conditions: XTDB handles this correctly

    record.set("condition_data", resource);

    return record;
  }

  // =========================================================================
  // EXTRACTION HELPERS
  // =========================================================================

  String extractPatientName(JsonNode resource) {
    JsonNode nameArray = resource.get("name");
    if (nameArray == null || !nameArray.isArray() || nameArray.isEmpty()) {
      return "Unknown";
    }

    JsonNode name = nameArray.get(0);
    for (JsonNode n : nameArray) {
      if ("official".equals(JsonUtil.getText(n, "use"))) {
        name = n;
        break;
      }
    }

    StringBuilder fullName = new StringBuilder();

    JsonNode given = name.get("given");
    if (given != null && given.isArray()) {
      for (JsonNode g : given) {
        if (!fullName.isEmpty()) fullName.append(" ");
        fullName.append(g.asText());
      }
    }

    String family = JsonUtil.getText(name, "family");
    if (family != null) {
      if (!fullName.isEmpty()) fullName.append(" ");
      fullName.append(family);
    }

    return fullName.isEmpty() ? "Unknown" : fullName.toString();
  }

  String extractEncounterType(JsonNode resource) {
    String code = JsonUtil.getText(resource, "class", "code");
    return code != null ? code : "UNKNOWN";
  }

  String extractSnomedCode(JsonNode resource) {
    JsonNode coding = resource.path("code").path("coding");
    if (coding.isArray()) {
      for (JsonNode c : coding) {
        String system = JsonUtil.getText(c, "system");
        if (system != null && system.contains("snomed")) {
          return JsonUtil.getText(c, "code");
        }
      }
      if (!coding.isEmpty()) {
        return JsonUtil.getText(coding.get(0), "code");
      }
    }
    return null;
  }

  String extractClinicalStatus(JsonNode resource) {
    JsonNode coding = resource.path("clinicalStatus").path("coding");
    if (coding.isArray() && !coding.isEmpty()) {
      return JsonUtil.getText(coding.get(0), "code");
    }
    return null;
  }

  String extractIdFromReference(String reference) {
    if (reference.startsWith("urn:uuid:")) {
      return reference.substring("urn:uuid:".length());
    }

    int slashIndex = reference.lastIndexOf('/');
    if (slashIndex >= 0) {
      return reference.substring(slashIndex + 1);
    }

    return reference;
  }

  // =========================================================================
  // LOGGING
  // =========================================================================

  private void logSummary(long duration) {
    logger.info("========================================");
    logger.info("Import complete in  {} ms", duration);
    logger.info("Files processed:    {}", filesProcessed);
    logger.info("Patients:           {}", patientsInserted);
    logger.info("Encounters:         {}", encountersInserted);
    logger.info("Conditions:         {}", conditionsInserted);
    logger.info("All Other Resources:{}", otherResourcesStored);
    logger.info("Errors:             {}", errors);
    logger.info("========================================");
  }

  // =========================================================================
  // GETTERS
  // =========================================================================

  public int getFilesProcessed() { return filesProcessed; }
  public int getPatientsInserted() { return patientsInserted; }
  public int getEncountersInserted() { return encountersInserted; }
  public int getConditionsInserted() { return conditionsInserted; }
  public int getOtherResourcesStored() { return otherResourcesStored; }
  public int getErrors() { return errors; }
}