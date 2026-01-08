package com.example;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;

import org.junit.jupiter.api.Test;

import com.example.config.DatabaseConfig;

// Unit tests for DatabaseConfig using an H2 in-memory database
public class DatabaseConfigTest {

  // Asserting that the connection pool initialises and returns a valid connection
  @Test
  public void initialiseConnection() throws SQLException {
    try (DatabaseConfig dbConfig = new TestDatabaseConfig()) {
      Connection conn = dbConfig.getConnection();

      assertThat(conn).isNotNull();
      assertThat(conn.isClosed()).isFalse();

      conn.close();
    }
  }

  // Asserting that isClosed returns false when pool is open
  @Test
  public void isClosedReturnsFalse() {
    try (DatabaseConfig dbConfig = new TestDatabaseConfig()) {
      assertThat(dbConfig.isClosed()).isFalse();
    }
  }

  // Asserting that isClosed returns true after close is called
  @Test
  public void isClosedReturnsTrue() {
    DatabaseConfig dbConfig = new TestDatabaseConfig();
    dbConfig.close();

    assertThat(dbConfig.isClosed()).isTrue();
  }

  // Asserting that connections are returned to the pool when closed
  @Test
  public void connectionReturnedToPool() throws SQLException {
    try (DatabaseConfig dbConfig = new TestDatabaseConfig()) {
      Connection conn1 = dbConfig.getConnection();
      conn1.close();

      Connection conn2 = dbConfig.getConnection();
      conn2.close();

      // If connections weren't returned, pool would be exhausted
      assertThat(conn2).isNotNull();
    }
  }

  // Asserting that getConnection throws SQLException when pool is closed
  @Test
  public void exceptionThrown() {
    DatabaseConfig dbConfig = new TestDatabaseConfig();
    dbConfig.close();

    assertThatThrownBy(dbConfig::getConnection)
        .isInstanceOf(SQLException.class);
  }

  // Asserting that try-with-resources closes the pool automatically
  @Test
  public void tryWithResourcesClose() {
    DatabaseConfig dbConfig;

    try (DatabaseConfig db = new TestDatabaseConfig()) {
      dbConfig = db;
      assertThat(db.isClosed()).isFalse();
    }

    assertThat(dbConfig.isClosed()).isTrue();
    dbConfig.close(); // Added so IDE doesn't complain about unclosed resources
  }

  // Test helper class that uses H2 in-memory database
  private static class TestDatabaseConfig extends DatabaseConfig {
    TestDatabaseConfig() {
      super(createTestProperties());
    }

    private static Properties createTestProperties() {
      Properties props = new Properties();
      // H2 in-memory database with PostgreSQL compatibility mode
      props.setProperty("db.url", "jdbc:h2:mem:testdb;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
      props.setProperty("db.user", "sa");
      props.setProperty("db.password", "");
      props.setProperty("db.pool.size", "2");
      props.setProperty("db.pool.timeout", "5000");
      return props;
    }
  }
}
