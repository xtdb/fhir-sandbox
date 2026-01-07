package com.example.util;

import java.io.File;
import java.io.IOException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

public final class JsonUtil {
  
  private static final ObjectMapper MAPPER = createMapper();

  // Private constructor to prevent instantiation
  private JsonUtil() {
    throw new AssertionError("Utility class: do not instantiate!");
  }
  
  // Called once when the class initially loads
  private static ObjectMapper createMapper() {
    ObjectMapper mapper = new ObjectMapper();

    // Register module to use Java 8+ date/time types (Instant, LocalDate, etc.)
    mapper.registerModule(new JavaTimeModule());

    // Write dates as ISO-8601 strings, rather than numeric timestamps
    mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    return mapper;
  }

  // Used to share the object mapper and work with it directly
  public static ObjectMapper getMapper() {
    return MAPPER;
  }

  /**
   * Parse a JSON file into a tree structure
   * 
   * @param file The JSON file to parse into a tree
   * @return The root JsonNode of the parsed tree
   * @throws IOException Thrown if the file cannot be read or parsed properly
   */
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
}

