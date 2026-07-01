package xtdb.fhir;

import com.fasterxml.jackson.databind.JsonNode;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * Strategy for writing a batch of same-typed FHIR resources to a target store.
 *
 * Used by {@link JdbcResourceSink} against a JDBC connection: {@link XtdbRecordsWriter}
 * writes straight to XTDB, {@link PostgresColumnWriter} writes to Postgres for CDC
 * replication. The target is selected at runtime by the
 * {@code patient-generator.target} property (see {@link SinkConfig}).
 */
public interface ResourceWriter {

  /**
   * Write a batch of resources of a single type using the given connection.
   *
   * @param conn         the database connection (transaction managed by the caller)
   * @param resourceType the snake-cased FHIR resource type (the target table name)
   * @param resources    the flattened resources to write (each carries an {@code _id})
   */
  void writeBatch(Connection conn, String resourceType, List<JsonNode> resources) throws SQLException;
}
