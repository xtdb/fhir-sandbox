package xtdb.fhir;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

/**
 * A destination for generated FHIR resources. Owns its own write lifecycle
 * (JDBC connection/transaction, message producer, …) so callers hand over whole
 * bundles without managing connections. Selected at runtime by the
 * {@code patient-generator.target} property (see {@link SinkConfig}).
 */
public interface ResourceSink extends AutoCloseable {

  /**
   * Write one bundle's resources, grouped by snake-cased resource type (the
   * target table name), as produced by
   * {@link FHIRImportService#extractResourcesByType(JsonNode)}.
   */
  void writeBundle(Map<String, List<JsonNode>> resourcesByType) throws Exception;

  /** Maximum bundles the generator may write concurrently against this sink. */
  int maxConcurrency();

  @Override
  default void close() throws Exception {}
}
