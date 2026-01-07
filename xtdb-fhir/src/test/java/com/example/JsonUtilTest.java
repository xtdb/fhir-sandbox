package com.example;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.util.JsonUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class JsonUtilTest {

  // Asserting that getMapper returns a mapper that isn't null
  @Test
  public void returnsNonNull() {
    ObjectMapper mapper = JsonUtil.getMapper();

    assertThat(mapper).isNotNull();
  }

  // Asserting that getMapper returns the same instance every time
  @Test
  public void returnsSameInstance() {
    ObjectMapper mapper1 = JsonUtil.getMapper();
    ObjectMapper mapper2 = JsonUtil.getMapper();

    assertThat(mapper1).isSameAs(mapper2);
  }

  // Asserting that parseFile can correctly parse a JSON file
  @Test
  public void parseJSONFile(@TempDir Path tempDir) throws IOException {
    Path JSONFile = tempDir.resolve("test.json");
    Files.writeString(JSONFile, """
        {
          "name": "John",
          "age": 30
        }
        """);

    JsonNode result = JsonUtil.parseFile(JSONFile.toFile());

    assertThat(result).isNotNull();
    assertThat(result.get("name").asText()).isEqualTo("John");
    assertThat(result.get("age").asInt()).isEqualTo(30);
  }

  // Asserting that parseFile throws an exception when the file doesn't exist or is invalid JSON
  @Test
  public void JSONThrowsException(@TempDir Path tempDir) throws IOException {
    Path JSONFile = tempDir.resolve("invalid.json");
    Files.writeString(JSONFile, "{ invalid json }");

    File nonExistent = new File("/nonexistent/file.json");

    assertThatThrownBy(() -> JsonUtil.parseFile(nonExistent))
        .isInstanceOf(IOException.class);

    assertThatThrownBy(() -> JsonUtil.parseFile(JSONFile.toFile()))
        .isInstanceOf(IOException.class);
  }

  // Asserting that getText can correctly extract a value from a nested path
  @Test
  public void valueForNestedPath() throws IOException {
    JsonNode node = JsonUtil.getMapper().readTree("""
        {
          "person": {
            "address": {
              "city": "New York"
            }
          }
        }
        """);

    String result = JsonUtil.getText(node, "person", "address", "city");
    assertThat(result).isEqualTo("New York");
  }


  // Asserting that getText returns null when the path doesn't exist, when the node is null, when the text value is not a string, and when the JSON value is explicitly null
  @Test
  public void returnsNull() throws IOException {
    JsonNode node = JsonUtil.getMapper().readTree("""
        {
          "name": "Bob"
        }
        """);

    String result = JsonUtil.getText(node, "nonexistent", "path");
    assertThat(result).isNull();

    result = JsonUtil.getText(null, "any", "path");
    assertThat(result).isNull();


    node = JsonUtil.getMapper().readTree("""
    {
      "count": 42,
      "active": true
    }
    """);

    assertThat(JsonUtil.getText(node, "count")).isNull();
    assertThat(JsonUtil.getText(node, "active")).isNull();


    node = JsonUtil.getMapper().readTree("""
    {
      "value": null
    }
    """);

    result = JsonUtil.getText(node, "value");
    assertThat(result).isNull();
  }

  // Asserting that toJsonString correctly converts a JsonNode to a JSON string
  @Test
  public void covertJSONToString() throws IOException {
    JsonNode node = JsonUtil.getMapper().readTree("""
        {"name":"Test","value":123}
        """);

    String result = JsonUtil.toJsonString(node);

    assertThat(result).isEqualTo("{\"name\":\"Test\",\"value\":123}");
  }
}
