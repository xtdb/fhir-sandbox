package com.example.config;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

public class DatabaseConfig implements AutoCloseable {
  private static final Logger logger = LoggerFactory.getLogger(DatabaseConfig.class);

  private final HikariDataSource dataSource;

  // Create database configuration and initialise connection pool. Reads settings from application.properties.
  public DatabaseConfig() {
    this.dataSource = createDataSource();
    logger.info("Database connection pool initialised");
  }

  // Constructor with custom properties
  protected DatabaseConfig(Properties props) {
    this.dataSource = createDataSource(props);
    logger.info("Database connection pool initialised");
  }

  /** 
   * Create a database connection pool from the application.properties file
   * 
   * @return The created HikariDataSource
   */ 
  private HikariDataSource createDataSource() {
    return createDataSource(loadProperties());
  }
  
  
  /**
   * Create a database connection pool from the given properties
   * 
   * @param props The properties to use
   * @return The created HikariDataSource
   */
  private HikariDataSource createDataSource(Properties props) {
    HikariConfig config = new HikariConfig();

    // Use direct URL if provided, otherwise build from components
    String jdbcUrl = props.getProperty("db.url");
    if (jdbcUrl == null) {
      // Build JDBC URL Defaults - XTDB uses PostgreSQL wire protocol
      String host = props.getProperty("db.host", "localhost");
      String port = props.getProperty("db.port", "5434");
      String database = props.getProperty("db.name", "xtdb");
      jdbcUrl = String.format("jdbc:postgresql://%s:%s/%s", host, port, database);
    }
    config.setJdbcUrl(jdbcUrl);

    // Credentials
    config.setUsername(props.getProperty("db.user", "xtdb"));

    // Pool settings
    config.setMaximumPoolSize(
        Integer.parseInt(props.getProperty("db.pool.size", "5"))
    );
    config.setConnectionTimeout(
        Long.parseLong(props.getProperty("db.pool.timeout", "30000"))
    );

    // Helpful name for debugging
    config.setPoolName("FhirImporter-Pool");

    logger.info("Connecting to XTDB at: {}", jdbcUrl);

    return new HikariDataSource(config);
  }

  /**
   * Load properties from application.properties
   * 
   * @return The loaded properties
   */
  private Properties loadProperties() {
    Properties props = new Properties();
    try (InputStream is = getClass().getClassLoader()
            .getResourceAsStream("application.properties")) {
      if (is != null) {
        props.load(is);
      } else {
        logger.warn("application.properties not found, using defaults");
      }
    } catch (IOException e) {
      logger.warn("Could not load application.properties: {}", e.getMessage());
    }
    return props;
  }

  /**
   * Get a connection from the pool.

   * @return A database connection from the pool
   * @throws SQLException If connection cannot be obtained
   */
  public Connection getConnection() throws SQLException {
    return dataSource.getConnection();
  }

  /**
   * Check if the connection pool is closed.
   *
   * @return true if the pool is closed, false otherwise
   */
  public boolean isClosed() {
    return dataSource == null || dataSource.isClosed();
  }

  // Closes the connection pool, which releases all resources. Call this when the application shuts down.
  @Override
  public void close() {
    if (dataSource != null && !dataSource.isClosed()) {
      dataSource.close();
      logger.info("Database connection pool closed");
    }
  }
}
