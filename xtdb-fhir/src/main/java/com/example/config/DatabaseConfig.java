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
  public DatabaseConfig(Properties props) {
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
   * Create a database connection pool from the given properties.
   * Cloud environment variables take precedence over local properties.
   *
   * @param props The properties to use as fallback
   * @return The created HikariDataSource
   */
  private HikariDataSource createDataSource(Properties props) {
    HikariConfig config = new HikariConfig();

    // Use direct URL if provided, otherwise build from components
    String jdbcUrl = getConfigValue("DB_URL", "db.url", props, null);
    if (jdbcUrl == null) {
      // Build JDBC URL using XTDB driver for full XTDB feature support
      String host = getConfigValue("DB_HOST", "db.host", props, "localhost");
      String port = getConfigValue("DB_PORT", "db.port", props, "5434");
      String database = getConfigValue("DB_NAME", "db.name", props, "xtdb");
      jdbcUrl = String.format("jdbc:xtdb://%s:%s/%s", host, port, database);
    }
    config.setJdbcUrl(jdbcUrl);

    // Credentials
    config.setUsername(getConfigValue("DB_USER", "db.user", props, "xtdb"));

    // Pool settings
    config.setMaximumPoolSize(
        Integer.parseInt(getConfigValue("DB_POOL_SIZE", "db.pool.size", props, "5"))
    );
    config.setConnectionTimeout(
        Long.parseLong(getConfigValue("DB_POOL_TIMEOUT", "db.pool.timeout", props, "30000"))
    );

    // Helpful name for debugging
    config.setPoolName("FhirImporter-Pool");

    logger.info("Connecting to XTDB at: {}", jdbcUrl);

    return new HikariDataSource(config);
  }

  public HikariDataSource getDataSource() {
    return dataSource;
  }

  /**
   * Get configuration value with cloud environment variable taking precedence over local properties.
   *
   * @param envVar Environment variable name
   * @param propKey Property key
   * @param props Properties to check
   * @param defaultValue Default value if neither is set
   * @return The resolved value
   */
  private String getConfigValue(String envVar, String propKey, Properties props, String defaultValue) {
    String envValue = System.getenv(envVar);
    if (envValue != null && !envValue.isEmpty()) {
      return envValue;
    }
    return props.getProperty(propKey, defaultValue);
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
