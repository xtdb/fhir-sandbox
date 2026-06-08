package xtdb.fhir;

import ca.uhn.fhir.model.api.TemporalPrecisionEnum;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.fasterxml.jackson.databind.node.ValueNode;
import org.hl7.fhir.r4.model.DateTimeType;
import org.postgresql.util.PGobject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import xtdb.fhir.util.JsonUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

/**
 * Writes resources straight into XTDB using its {@code INSERT … RECORDS} syntax:
 * one table per resource type, schemaless documents keyed by {@code _id}.
 * Applies XTDB's JSON-LD date coercion and chunks batches to stay under Kafka's
 * 1MB message-size limit.
 */
public class XtdbRecordsWriter implements ResourceWriter {

  private static final Logger logger = LoggerFactory.getLogger(XtdbRecordsWriter.class);

  // Max records per batch to stay under Kafka's 1MB message size limit.
  // Some records (like imaging_study) can be very large (~50KB+), so use small batches.
  private static final int MAX_BATCH_SIZE = 10;

  @Override
  public void writeBatch(Connection conn, String resourceType, List<JsonNode> resources)
      throws SQLException {
    if (resources.isEmpty()) return;

    // XTDB-specific: coerce date/time strings into XTDB JSON-LD typed values.
    for (JsonNode resource : resources) {
      JsonUtil.convertValues(resource, v -> {
        var d = toJsonLdDateOrNull(v);
        return d == null ? v : d;
      });
    }

    String sql = String.format("INSERT INTO %s RECORDS ?", resourceType);

    // Process in chunks to avoid exceeding Kafka's message size limit.
    for (int i = 0; i < resources.size(); i += MAX_BATCH_SIZE) {
      int end = Math.min(i + MAX_BATCH_SIZE, resources.size());
      List<JsonNode> chunk = resources.subList(i, end);

      try (PreparedStatement ps = conn.prepareStatement(sql)) {
        for (var record : chunk) {
          PGobject jsonObject = new PGobject();
          jsonObject.setType("json");
          jsonObject.setValue(record.toString());

          ps.setObject(1, jsonObject);
          ps.addBatch();
        }
        ps.executeBatch();
      }
    }
    logger.debug("inserted {} resources of type {} (xtdb)", resources.size(), resourceType);
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
        } else if (asFhirDate.getPrecision().ordinal() >= TemporalPrecisionEnum.MINUTE.ordinal()) {
          var dateJsonLd = JsonNodeFactory.instance.objectNode();
          dateJsonLd.set("@type", new TextNode("xt:timestamptz"));
          dateJsonLd.set("@value", new TextNode(v.textValue()));
          return dateJsonLd;
        }
      } catch (ca.uhn.fhir.parser.DataFormatException ignored) {}
    }
    return null;
  }
}
