package com.example.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import xtdb.fhir.FHIRImportService;
import xtdb.fhir.XtdbRecordsWriter;

@Disabled
// Integration tests for FHIRImportService with a real XTDB database running.
public class XTDBServiceTest {

  private FHIRImportService xtdbService;
  private boolean xtdbAvailable;
  private HikariDataSource dataSource;


  @BeforeEach
  public void setUp() {
    try {
      HikariConfig config = new HikariConfig();
      config.setJdbcUrl("jdbc:xtdb://localhost:5434/xtdb");
      config.setUsername("xtdb");
      config.setMaximumPoolSize(5);
      config.setConnectionTimeout(30000);
      dataSource = new HikariDataSource(config);
      try (Connection conn = dataSource.getConnection()) {
        xtdbAvailable = conn.isValid(5);
      }
      xtdbService = new FHIRImportService(dataSource, new XtdbRecordsWriter());
    } catch (Exception e) {
      xtdbAvailable = false;
    }
  }

  @AfterEach
  public void tearDown() {
    if (dataSource != null) {
      dataSource.close();
    }
  }

  // Assert that you can import a JSON patient bundle and verify it was inserted
  @Test
  public void importPatient(@TempDir Path tempDir) throws IOException, SQLException {
    assumeThat(xtdbAvailable).as("XTDB not available").isTrue();

    long id = System.currentTimeMillis();
    // Create a test bundle file with a unique (enough) ID
    String bundle = String.format("""
        {
          "resourceType": "Bundle",
          "type": "transaction",
          "entry": [
            {
              "resource": {
                "resourceType": "Patient",
                "id": "%s",
                "gender": "female",
                "birthDate": "1990-05-20",
                "name": [{"family": "TestFamily", "given": ["TestGiven"]}]
              }
            }
          ]
        }
        """, id);

    Path bundleFile = tempDir.resolve("test-bundle.json");
    Files.writeString(bundleFile, bundle);

    // Import
    xtdbService.importFile(bundleFile);

    // Verify
    assertThat(xtdbService.getPatientsInserted()).isEqualTo(1);

    try (Connection conn = dataSource.getConnection();
         Statement stmt = conn.createStatement();
         ResultSet rs = stmt.executeQuery(
             "SELECT _id, name, gender, birth_date FROM patients WHERE _id = '" + id + "'")) {

      assertThat(rs.next()).isTrue();
      assertThat(rs.getString("name")).isEqualTo("TestGiven TestFamily");
      assertThat(rs.getString("gender")).isEqualTo("female");
    }
  }

  // Assert that you can import a JSON bundle with XTDB temporality and verify it was inserted, and that the temporal data can be correctly queried
  @Test
  public void importWithTemporality(@TempDir Path tempDir) throws IOException, SQLException {
    assumeThat(xtdbAvailable).as("XTDB not available").isTrue();

    String bundle = """
        {
          "resourceType": "Bundle",
          "entry": [
            {
              "resource": {
                "resourceType": "Patient",
                "id": "patient-temporal-test"
              }
            },
            {
              "resource": {
                "resourceType": "Condition",
                "id": "condition-temporal-test",
                "subject": {"reference": "Patient/patient-temporal-test"},
                "code": {
                  "coding": [{"system": "http://snomed.info/sct", "code": "73211009"}]
                },
                "clinicalStatus": {"coding": [{"code": "resolved"}]},
                "onsetDateTime": "2020-01-15T00:00:00Z",
                "abatementDateTime": "2020-06-30T00:00:00Z"
              }
            }
          ]
        }
        """;

    Path bundleFile = tempDir.resolve("temporal-bundle.json");
    Files.writeString(bundleFile, bundle);

    xtdbService.importFile(bundleFile);

    assertThat(xtdbService.getConditionsInserted()).isEqualTo(1);

    // Query using XTDB temporal features
    try (Connection conn = dataSource.getConnection();
         Statement stmt = conn.createStatement()) {

      // Condition should be visible in March 2020 (between onset and abatement)
      try (ResultSet rs = stmt.executeQuery("""
          SELECT _id, snomed_code, clinical_status 
          FROM conditions 
          FOR VALID_TIME AS OF TIMESTAMP '2020-03-15T00:00:00Z'
          WHERE _id = 'condition-temporal-test'
          """)) {
        assertThat(rs.next()).as("Condition should be visible in March 2020").isTrue();
        assertThat(rs.getString("snomed_code")).isEqualTo("73211009");
      }

      // Condition should NOT be visible in August 2020 (after abatement)
      try (ResultSet rs = stmt.executeQuery("""
          SELECT _id 
          FROM conditions 
          FOR VALID_TIME AS OF TIMESTAMP '2020-08-15T00:00:00Z'
          WHERE _id = 'condition-temporal-test'
          """)) {
        assertThat(rs.next()).as("Condition should NOT be visible in August 2020").isFalse();
      }
    }
  }

  // Assert that you can import a JSON bundle with multiple resource types and verify they were all inserted properly
  @Test
  public void importMultiResourceTypes(@TempDir Path tempDir) throws IOException, SQLException {
    assumeThat(xtdbAvailable).as("XTDB not available").isTrue();

    String bundle = """
        {
          "resourceType": "Bundle",
          "entry": [
            {"resource": {"resourceType": "Patient", "id": "p1", "gender": "male"}},
            {"resource": {"resourceType": "Patient", "id": "p2", "gender": "female"}},
            {"resource": {
              "resourceType": "Encounter", 
              "id": "e1", 
              "subject": {"reference": "Patient/p1"},
              "class": {"code": "AMB"},
              "period": {"start": "2023-01-01T09:00:00Z", "end": "2023-01-01T10:00:00Z"}
            }},
            {"resource": {
              "resourceType": "Condition",
              "id": "c1",
              "subject": {"reference": "Patient/p1"},
              "encounter": {"reference": "Encounter/e1"},
              "onsetDateTime": "2023-01-01T09:30:00Z"
            }}
          ]
        }
        """;

    Path bundleFile = tempDir.resolve("multi-bundle.json");
    Files.writeString(bundleFile, bundle);

    xtdbService.importFile(bundleFile);

    assertThat(xtdbService.getPatientsInserted()).isEqualTo(2);
    assertThat(xtdbService.getEncountersInserted()).isEqualTo(1);
    assertThat(xtdbService.getConditionsInserted()).isEqualTo(1);
    assertThat(xtdbService.getFilesProcessed()).isEqualTo(1);
    assertThat(xtdbService.getErrors()).isZero();
  }

  // Assert that you can import a JSON bundle with a condition that has no abatement date and verify that it still works into the future (NOW)
  @Test
  public void importConditionNoAbatement(@TempDir Path tempDir) throws IOException, SQLException {
    assumeThat(xtdbAvailable).as("XTDB not available").isTrue();

    String bundle = """
        {
          "resourceType": "Bundle",
          "entry": [
            {
              "resource": {
                "resourceType": "Patient",
                "id": "patient-temporal-test2"
              }
            },
            {
              "resource": {
                "resourceType": "Condition",
                "id": "condition-temporal-test2",
                "subject": {"reference": "Patient/patient-temporal-test2"},
                "code": {
                  "coding": [{"system": "http://snomed.info/sct", "code": "73211009"}]
                },
                "clinicalStatus": {"coding": [{"code": "resolved"}]},
                "onsetDateTime": "2020-01-15T00:00:00Z"
              }
            }
          ]
        }
        """;

    Path bundleFile = tempDir.resolve("temporal-bundle2.json");
    Files.writeString(bundleFile, bundle);

    xtdbService.importFile(bundleFile);

    assertThat(xtdbService.getConditionsInserted()).isEqualTo(1);

    // Query using XTDB temporal features
    try (Connection conn = dataSource.getConnection();
         Statement stmt = conn.createStatement()) {

      // Condition should be visible in March 2020
      try (ResultSet rs = stmt.executeQuery("""
          SELECT _id, snomed_code, clinical_status 
          FROM conditions 
          FOR VALID_TIME AS OF TIMESTAMP '2020-03-15T00:00:00Z'
          WHERE _id = 'condition-temporal-test2'
          """)) {
        assertThat(rs.next()).as("Condition should be visible in March 2020").isTrue();
        assertThat(rs.getString("snomed_code")).isEqualTo("73211009");
      }

      // Condition should still be visible, whatever valid time it is when this test is run!
      try (ResultSet rs = stmt.executeQuery("""
          SELECT _id, snomed_code
          FROM conditions
          WHERE _id = 'condition-temporal-test2'
          """)) {
        assertThat(rs.next()).as("Condition should still be visible now").isTrue();
        assertThat(rs.getString("snomed_code")).isEqualTo("73211009");
      }
    }
  }
}