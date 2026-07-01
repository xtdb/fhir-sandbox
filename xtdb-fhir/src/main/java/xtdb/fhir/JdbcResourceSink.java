package xtdb.fhir;

import com.fasterxml.jackson.databind.JsonNode;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/**
 * A {@link ResourceSink} backed by a JDBC {@link DataSource}: borrows a
 * connection per bundle and delegates the per-type writes to a
 * {@link ResourceWriter} ({@link XtdbRecordsWriter} straight to XTDB, or
 * {@link PostgresColumnWriter} to Postgres for CDC).
 */
public final class JdbcResourceSink implements ResourceSink {

  private final DataSource dataSource;
  private final ResourceWriter writer;
  private final int maxConcurrency;

  public JdbcResourceSink(DataSource dataSource, ResourceWriter writer, int maxConcurrency) {
    this.dataSource = dataSource;
    this.writer = writer;
    this.maxConcurrency = maxConcurrency;
  }

  @Override
  public void writeBundle(Map<String, List<JsonNode>> resourcesByType) throws SQLException {
    try (Connection conn = dataSource.getConnection()) {
      for (var entry : resourcesByType.entrySet()) {
        writer.writeBatch(conn, entry.getKey(), entry.getValue());
      }
    }
  }

  @Override
  public int maxConcurrency() {
    return maxConcurrency;
  }
}
