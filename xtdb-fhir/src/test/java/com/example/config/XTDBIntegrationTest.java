package com.example.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;

import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Random;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PGobject;


/**
 * Integration tests that connect to a real XTDB instance.
 *
 * These tests require XTDB to be running locally - tests are skipped automatically if XTDB is not available.
 *
 * Tests use unique table names to avoid conflicts between runs.
 */
public class XTDBIntegrationTest {

  private DatabaseConfig dbConfig;
  private boolean xtdbAvailable;
  private static final Random random = new Random();

  @BeforeEach
  public void setUp() {
    try {
      dbConfig = new DatabaseConfig();
      // Test if we can actually connect
      try (Connection conn = dbConfig.getConnection()) {
        xtdbAvailable = conn.isValid(5);
      }
    } catch (Exception e) {
      xtdbAvailable = false;
    }
  }

  @AfterEach
  public void tearDown() {
    if (dbConfig != null) {
      dbConfig.close();
    }
  }

  // Helper to generate unique table names
  private String uniqueTable(String prefix) {
    return prefix + "_" + System.currentTimeMillis() + "_" + random.nextInt(10000);
  }

  // Asserting that we can connect to XTDB and the connection is valid
  @Test
  public void connectsToXtdb() throws SQLException {
    assumeThat(xtdbAvailable).as("XTDB not available, skipping integration test").isTrue();

    try (Connection conn = dbConfig.getConnection()) {
      assertThat(conn).isNotNull();
      assertThat(conn.isValid(5)).isTrue();
    }
  }

  // Asserting that we can create a table, insert data, and query it
  @Test
  public void createTableInsertAndQuery() throws SQLException {
    assumeThat(xtdbAvailable).as("XTDB not available, skipping integration test").isTrue();

    String table = uniqueTable("test_table");
    try (Connection conn = dbConfig.getConnection();
         Statement stmt = conn.createStatement()) {

      // Insert test data
      stmt.execute(String.format(
          "INSERT INTO %s (_id, name, value) VALUES ('1', 'test', 42)", table));

      // Query and verify
      try (ResultSet rs = stmt.executeQuery(
          String.format("SELECT name, value FROM %s WHERE _id = '1'", table))) {
        assertThat(rs.next()).isTrue();
        assertThat(rs.getString("name")).isEqualTo("test");
        assertThat(rs.getInt("value")).isEqualTo(42);
      }
    }
  }

  // Asserting that multiple connections from the pool work correctly
  @Test
  public void multipleConnectionsFromPool() throws SQLException {
    assumeThat(xtdbAvailable).as("XTDB not available, skipping integration test").isTrue();

    try (Connection conn1 = dbConfig.getConnection();
         Connection conn2 = dbConfig.getConnection()) {

      assertThat(conn1).isNotNull();
      assertThat(conn2).isNotNull();
      assertThat(conn1).isNotSameAs(conn2);

      assertThat(conn1.isValid(5)).isTrue();
      assertThat(conn2.isValid(5)).isTrue();
    }
  }

  // Asserting that connections are properly returned to the pool
  @Test
  public void connectionPoolReuse() throws SQLException {
    assumeThat(xtdbAvailable).as("XTDB not available, skipping integration test").isTrue();

    // Get and close several connections
    for (int i = 0; i < 10; i++) {
      try (Connection conn = dbConfig.getConnection()) {
        assertThat(conn.isValid(1)).isTrue();
      }
    }

    // Pool should still be healthy
    assertThat(dbConfig.isClosed()).isFalse();
  }

  // Asserting that multiple connections can insert concurrently and be queried
  @Test
  public void concurrentInsertsWithOverwrite() throws SQLException {
    assumeThat(xtdbAvailable).as("XTDB not available, skipping integration test").isTrue();

    String table = uniqueTable("concurrency_test");

    // Use 5 different connections to insert 4 rows (2 share the same _id, so one overwrites)
    try (Connection conn1 = dbConfig.getConnection();
         Connection conn2 = dbConfig.getConnection();
         Connection conn3 = dbConfig.getConnection();
         Connection conn4 = dbConfig.getConnection();
         Connection conn5 = dbConfig.getConnection()) {

      // Threads to perform inserts concurrently
      Thread t1 = new Thread(() -> {
        try (Statement stmt = conn1.createStatement()) {
          stmt.execute(String.format(
              "INSERT INTO %s (_id, name, value) VALUES ('1', 'Alice', 100)", table));
        } catch (SQLException e) {
          throw new RuntimeException(e);
        }
      });

      Thread t2 = new Thread(() -> {
        try (Statement stmt = conn2.createStatement()) {
          stmt.execute(String.format(
              "INSERT INTO %s (_id, name, value) VALUES ('2', 'Bob', 200)", table));
        } catch (SQLException e) {
          throw new RuntimeException(e);
        }
      });

      Thread t3 = new Thread(() -> {
        try (Statement stmt = conn3.createStatement()) {
          stmt.execute(String.format(
              "INSERT INTO %s (_id, name, value) VALUES ('3', 'Charlie', 300)", table));
        } catch (SQLException e) {
          throw new RuntimeException(e);
        }
      });

      // Start threads
      t1.start();
      t2.start();
      t3.start();

      // Wait for threads to finish
      try {
        t1.join();
        t2.join();
        t3.join();
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }

      // Connection 4: Insert row with same _id = '3' but different value (overwrites conn3's insert)
      try (Statement stmt = conn4.createStatement()) {
        stmt.execute(String.format(
            "INSERT INTO %s (_id, name, value) VALUES ('3', 'Charlie', 350)", table));
      }

      // Connection 5: Query to verify all data is visible
      try (Statement stmt = conn5.createStatement();
           ResultSet rs = stmt.executeQuery(String.format(
               "SELECT _id, name, value FROM %s ORDER BY _id", table))) {

        // Row 1: Alice
        assertThat(rs.next()).isTrue();
        assertThat(rs.getString("_id")).isEqualTo("1");
        assertThat(rs.getString("name")).isEqualTo("Alice");
        assertThat(rs.getInt("value")).isEqualTo(100);

        // Row 2: Bob
        assertThat(rs.next()).isTrue();
        assertThat(rs.getString("_id")).isEqualTo("2");
        assertThat(rs.getString("name")).isEqualTo("Bob");
        assertThat(rs.getInt("value")).isEqualTo(200);

        // Row 3: Charlie with overwritten value (350, not 300)
        assertThat(rs.next()).isTrue();
        assertThat(rs.getString("_id")).isEqualTo("3");
        assertThat(rs.getString("name")).isEqualTo("Charlie");
        assertThat(rs.getInt("value")).isEqualTo(350);

        // No more rows (only 3 unique _ids)
        assertThat(rs.next()).isFalse();
      }
    }
  }

  // ==================== XTDB Driver-Specific Feature Tests ====================

  // Asserting that XTDB RECORDS syntax works for JSON-like object insertion
  @Test
  public void insertRecords() throws SQLException {
    assumeThat(xtdbAvailable).as("XTDB not available, skipping integration test").isTrue();

    String table = uniqueTable("records_test");
    try (Connection conn = dbConfig.getConnection();
         Statement stmt = conn.createStatement()) {

      // XTDB-specific RECORDS syntax allows JSON-like object insertion
      stmt.execute(String.format(
          "INSERT INTO %s RECORDS {_id: '1', name: 'John Doe', age: 45, active: true}",
          table));

      try (ResultSet rs = stmt.executeQuery(String.format(
          "SELECT _id, name, age, active FROM %s WHERE _id = '1'", table))) {
        assertThat(rs.next()).isTrue();
        assertThat(rs.getString("_id")).isEqualTo("1");
        assertThat(rs.getString("name")).isEqualTo("John Doe");
        assertThat(rs.getInt("age")).isEqualTo(45);
        assertThat(rs.getBoolean("active")).isTrue();
      }
    }
  }

  // Asserting that multiple records can be inserted in a single RECORDS statement
  @Test
  public void multipleRecords() throws SQLException {
    assumeThat(xtdbAvailable).as("XTDB not available, skipping integration test").isTrue();

    String table = uniqueTable("multi_records_test");
    try (Connection conn = dbConfig.getConnection();
         Statement stmt = conn.createStatement()) {

      // Insert multiple records at once using RECORDS syntax
      stmt.execute(String.format(
          "INSERT INTO %s RECORDS " +
          "{_id: '1', type: 'Patient', name: 'Alice'}, " +
          "{_id: '2', type: 'Patient', name: 'Bob'}, " +
          "{_id: '3', type: 'Patient', name: 'Charlie'}",
          table));

      try (ResultSet rs = stmt.executeQuery(String.format(
          "SELECT COUNT(*) as count FROM %s", table))) {
        assertThat(rs.next()).isTrue();
        assertThat(rs.getLong("count")).isEqualTo(3);
      }
      try (ResultSet rs = stmt.executeQuery(String.format(
          "SELECT _id FROM %s ORDER BY _id ASC", table))) {
        assertThat(rs.next()).isTrue();
        assertThat(rs.getString("_id")).isEqualTo("1");
        assertThat(rs.next()).isTrue();
        assertThat(rs.getString("_id")).isEqualTo("2");
        assertThat(rs.next()).isTrue();
        assertThat(rs.getString("_id")).isEqualTo("3");
        // No more rows
        assertThat(rs.next()).isFalse();
      }
    }
  }

  // Asserting that JSON can be inserted using PGobject with json type
  @Test
  public void insertJSON() throws SQLException {
    assumeThat(xtdbAvailable).as("XTDB not available, skipping integration test").isTrue();

    String table = uniqueTable("json_test");
    try (Connection conn = dbConfig.getConnection()) {

      // Create a simple JSON record
      String patientJson = """
          {"_id": "123", "name": "John Smith", "age": 45, "active": true}
          """;

      // Use PGobject with "json" type: XTDB driver feature
      try (PreparedStatement pstmt = conn.prepareStatement(String.format(
          "INSERT INTO %s RECORDS ?", table))) {

        PGobject jsonObject = new PGobject();
        jsonObject.setType("json");
        jsonObject.setValue(patientJson);

        pstmt.setObject(1, jsonObject);
        pstmt.execute();
      }

      // Query and verify
      try (Statement stmt = conn.createStatement();
           ResultSet rs = stmt.executeQuery(String.format("SELECT _id, name, age, active FROM %s", table))) {
        assertThat(rs.next()).isTrue();
        assertThat(rs.getString("_id")).isEqualTo("123");
        assertThat(rs.getString("name")).isEqualTo("John Smith");
        assertThat(rs.getInt("age")).isEqualTo(45);
        assertThat(rs.getBoolean("active")).isTrue();
      }
    }
  }

  // Asserting that nested arrays are returned correctly from XTDB
  @Test
  public void nestedArrayInJSON() throws SQLException {
    assumeThat(xtdbAvailable).as("XTDB not available, skipping integration test").isTrue();

    String table = uniqueTable("array_test");
    try (Connection conn = dbConfig.getConnection()) {

      // Insert record with array field using JSON
      String jsonWithArray = """
          {"_id": "1", "code": "blood-pressure", "components": ["systolic", "diastolic"]}
          """;

      try (PreparedStatement pstmt = conn.prepareStatement(String.format(
          "INSERT INTO %s RECORDS ?", table))) {

        PGobject jsonObject = new PGobject();
        jsonObject.setType("json");
        jsonObject.setValue(jsonWithArray);

        pstmt.setObject(1, jsonObject);
        pstmt.execute();
      }

      // Query and verify array is returned
      try (Statement stmt = conn.createStatement();
           ResultSet rs = stmt.executeQuery(String.format(
               "SELECT _id, code, components FROM %s", table))) {
        assertThat(rs.next()).isTrue();
        assertThat(rs.getString("code")).isEqualTo("blood-pressure");

        // XTDB returns arrays as SQL Arrays
        Array componentsArray = rs.getArray("components");
        assertThat(componentsArray).isNotNull();
        String[] components = (String[]) componentsArray.getArray();
        assertThat(components).containsExactly("systolic", "diastolic");
      }
    }
  }

  // Asserting that parameterised queries work correctly with XTDB driver, ensuring that SQL injection is prevented
  @Test
  public void parameterisedQueries() throws SQLException {
    assumeThat(xtdbAvailable).as("XTDB not available, skipping integration test").isTrue();

    String table = uniqueTable("param_test");
    try (Connection conn = dbConfig.getConnection();
         Statement stmt = conn.createStatement()) {

      // Insert test data using RECORDS syntax
      stmt.execute(String.format(
          "INSERT INTO %s RECORDS " +
          "{_id: '3', patient_id: '1', status: 'finished'}, " +
          "{_id: '2', patient_id: '1', status: 'in-progress'}, " +
          "{_id: '1', patient_id: '2', status: 'finished'}",
          table));

      // Query with parameters
      try (PreparedStatement pstmt = conn.prepareStatement(String.format(
          "SELECT _id, status FROM %s WHERE patient_id = ? AND status = ?", table))) {

        pstmt.setString(1, "1");
        pstmt.setString(2, "finished");

        try (ResultSet rs = pstmt.executeQuery()) {
          assertThat(rs.next()).isTrue();
          assertThat(rs.getString("_id")).isEqualTo("3");
          assertThat(rs.getString("status")).isEqualTo("finished");
          assertThat(rs.next()).isFalse(); // Only one match
        }
      }
    }
  }
}
