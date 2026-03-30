package xtdb.fhir.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ValueNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;
import java.util.function.Function;

public final class JsonUtil {
  
  private static final ObjectMapper MAPPER = createMapper();

  private static ObjectMapper createMapper() {
    ObjectMapper mapper = new ObjectMapper();

    // Register module to use Java 8+ date/time types (Instant, LocalDate, etc.)
    mapper.registerModule(new JavaTimeModule());

    // Write dates as ISO-8601 strings, rather than numeric timestamps
    mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    return mapper;
  }

  public static ObjectMapper getMapper() {
    return MAPPER;
  }

  public static JsonNode parseFile(File file) throws IOException {
    return MAPPER.readTree(file);
  }

  /**
   * SAFELY get a text value from a give JSON path
   * 
   * @param node The starting node (root of the JSON you're navigating)
   * @param path An array of different field names we are going to navigate along the tree to get to the needed text
   * @return The text value of the given field path, or NULL if path doesn't exist or is text
   */
  public static String getText(JsonNode node, String... path) {
    JsonNode current = node;
    for (String field: path) {
      if (current == null || current.isMissingNode() || current.isNull()) {
        return null;
      }
      current = current.get(field);
    }
    return (current != null && current.isTextual()) ? current.asText() : null;
  }

  /**
   * Convert a JSonNode to its JSON string representation
   * 
   * @param node The node being converted
   * @return The JSON string
   * @throws IOException If the serialisation fails
   */
  public static String toJsonString(JsonNode node) throws IOException {
    return MAPPER.writeValueAsString(node);
  }

  public static void convertKeysToSnakeCase(JsonNode node) {
    if (node.isObject()) {
      Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
      var fieldNames = new ArrayList<String>();
      fields.forEachRemaining(entry -> fieldNames.add(entry.getKey()));

      for (String fieldName : fieldNames) {
        JsonNode value = ((ObjectNode) node).remove(fieldName);
        ((ObjectNode) node).set(toSnakeCase(fieldName), value);

        convertKeysToSnakeCase(value);
      }
    } else if (node.isArray()) {
      for (JsonNode element : node) {
        convertKeysToSnakeCase(element);
      }
    }
  }

  public static String toSnakeCase(String s) {
    return PropertyNamingStrategies.SnakeCaseStrategy.INSTANCE.translate(s);
  }

  public static void convertValues(JsonNode node, Function<ValueNode, JsonNode> convert) {
    if (node.isObject()) {
      var objectNode = (ObjectNode) node;
      var fields = objectNode.fields();
      while (fields.hasNext()) {
        var field = fields.next();
        JsonNode value = field.getValue();
        if (value.isValueNode()) {
          objectNode.set(field.getKey(), convert.apply((ValueNode) value));
        } else {
          convertValues(value, convert);
        }
      }
    } else if (node.isArray()) {
      ArrayNode arrayNode = (ArrayNode) node;
      for (int i = 0; i < arrayNode.size(); i++) {
        JsonNode element = arrayNode.get(i);
        if (element.isValueNode()) {
          arrayNode.set(i, convert.apply((ValueNode) element));
        } else {
          convertValues(element, convert);
        }
      }
    }
  }
}
