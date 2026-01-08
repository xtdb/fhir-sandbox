package com.example.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Integration tests that connect to a real XTDB instance.
 *
 * These tests require XTDB to be running locally - tests are skipped automatically if XTDB is not available.
 */
public class XTDBIntegrationTest {

  private DatabaseConfig dbConfig;
  private boolean xtdbAvailable;

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
      // Clean up any test tables we created
      if (xtdbAvailable) {
        try (Connection conn = dbConfig.getConnection();
             Statement stmt = conn.createStatement()) {
          stmt.execute("DROP TABLE IF EXISTS test_table");
        } catch (SQLException e) {
          // Ignore cleanup errors
        }
      }
      dbConfig.close();
    }
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

    try (Connection conn = dbConfig.getConnection();
         Statement stmt = conn.createStatement()) {

      // Insert test data
      stmt.execute("INSERT INTO test_table (_id, name, value) VALUES ('1', 'test', 42)");

      // Query and verify
      try (ResultSet rs = stmt.executeQuery("SELECT name, value FROM test_table WHERE _id = '1'")) {
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

    // Use 5 different connections to insert 4 rows (2 share the same _id, so one overwrites)
    try (Connection conn1 = dbConfig.getConnection();
         Connection conn2 = dbConfig.getConnection();
         Connection conn3 = dbConfig.getConnection();
         Connection conn4 = dbConfig.getConnection();
         Connection conn5 = dbConfig.getConnection()) {

      // Threads to perform inserts concurrently
      Thread t1 = new Thread(() -> {
        try (Statement stmt = conn1.createStatement()) {
          stmt.execute("INSERT INTO test_table (_id, name, value) VALUES ('1', 'Alice', 100)");
        } catch (SQLException e) {
          throw new RuntimeException(e);
        }
      });

      Thread t2 = new Thread(() -> {
        try (Statement stmt = conn2.createStatement()) {
          stmt.execute("INSERT INTO test_table (_id, name, value) VALUES ('2', 'Bob', 200)");
        } catch (SQLException e) {
          throw new RuntimeException(e);
        }
      });

      Thread t3 = new Thread(() -> {
        try (Statement stmt = conn3.createStatement()) {
          stmt.execute("INSERT INTO test_table (_id, name, value) VALUES ('3', 'Charlie', 300)");
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
        stmt.execute("INSERT INTO test_table (_id, name, value) VALUES ('3', 'Charlie', 350)");
      }

      // Connection 5: Query to verify all data is visible
      try (Statement stmt = conn5.createStatement();
           ResultSet rs = stmt.executeQuery("SELECT _id, name, value FROM test_table ORDER BY _id")) {

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
}
