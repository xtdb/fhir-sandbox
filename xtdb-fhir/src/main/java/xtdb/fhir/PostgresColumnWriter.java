package xtdb.fhir;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import xtdb.fhir.util.JsonUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Writes resources into PostgreSQL for CDC replication into XTDB: one table per
 * resource type in the {@code core} schema (the publication scope), each row
 * keyed by {@code _id}. Rather than carrying the full resource as jsonb (which we
 * don't rely on replicating), a handful of top-level FHIR fields are promoted into
 * flat typed columns that CDC handles cleanly. Upserts on {@code _id} to match
 * XTDB RECORDS semantics. Tables are created on first sight of a resource type.
 */
public class PostgresColumnWriter implements ResourceWriter {

  private static final Logger logger = LoggerFactory.getLogger(PostgresColumnWriter.class);

  // Resource types whose table we've already ensured exists this process.
  private final Set<String> ensured = ConcurrentHashMap.newKeySet();

  @Override
  public void writeBatch(Connection conn, String resourceType, List<JsonNode> resources)
      throws SQLException {
    if (resources.isEmpty()) return;

    ensureTable(conn, resourceType);

    String sql = "INSERT INTO core.\"" + resourceType
        + "\" (_id, resource_type, status, patient_id, encounter_id, code, last_updated) "
        + "VALUES (?, ?, ?, ?, ?, ?, ?::timestamptz) "
        + "ON CONFLICT (_id) DO UPDATE SET "
        + "resource_type = EXCLUDED.resource_type, status = EXCLUDED.status, "
        + "patient_id = EXCLUDED.patient_id, encounter_id = EXCLUDED.encounter_id, "
        + "code = EXCLUDED.code, last_updated = EXCLUDED.last_updated";

    try (PreparedStatement ps = conn.prepareStatement(sql)) {
      for (JsonNode record : resources) {
        ps.setString(1, JsonUtil.getText(record, "_id"));
        ps.setString(2, resourceType);
        ps.setString(3, JsonUtil.getText(record, "status"));
        ps.setString(4, patientId(record));
        ps.setString(5, encounterId(record));
        ps.setString(6, code(record));
        setNullable(ps, 7, lastUpdated(record));
        ps.addBatch();
      }
      ps.executeBatch();
    }
    logger.debug("upserted {} resources of type {} (postgres)", resources.size(), resourceType);
  }

  // One table per resource type, created on first sight. CREATE … IF NOT EXISTS
  // is safe under concurrency; the in-process cache just avoids re-issuing DDL.
  private void ensureTable(Connection conn, String resourceType) throws SQLException {
    if (ensured.contains(resourceType)) return;
    try (Statement st = conn.createStatement()) {
      st.execute("CREATE SCHEMA IF NOT EXISTS core");
      st.execute("CREATE TABLE IF NOT EXISTS core.\"" + resourceType + "\" ("
          + "_id text PRIMARY KEY, "
          + "resource_type text, "
          + "status text, "
          + "patient_id text, "
          + "encounter_id text, "
          + "code text, "
          + "last_updated timestamptz, "
          + "created_at timestamptz NOT NULL DEFAULT now())");
    }
    ensured.add(resourceType);
  }

  private static void setNullable(PreparedStatement ps, int idx, String value) throws SQLException {
    if (value != null) {
      ps.setString(idx, value);
    } else {
      ps.setNull(idx, Types.VARCHAR);
    }
  }

  // patient_id convenience column: the patient/subject reference, already resolved
  // to an id by the bundle extractor (setReferenceAsId).
  private static String patientId(JsonNode record) {
    String pid = JsonUtil.getText(record, "patient_id");
    return pid != null ? pid : JsonUtil.getText(record, "subject_id");
  }

  // encounter_id convenience column: derived from the encounter reference if present.
  private static String encounterId(JsonNode record) {
    String ref = JsonUtil.getText(record, "encounter", "reference");
    return ref != null ? FHIRImportService.extractIdFromReference(ref) : null;
  }

  // code column: the resource's primary code, preferring a SNOMED coding.
  private static String code(JsonNode record) {
    JsonNode coding = record.path("code").path("coding");
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

  // last_updated column: meta.lastUpdated (snake-cased to last_updated by the extractor).
  private static String lastUpdated(JsonNode record) {
    return JsonUtil.getText(record, "meta", "last_updated");
  }
}
