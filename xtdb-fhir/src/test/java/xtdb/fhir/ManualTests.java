package xtdb.fhir;

import com.example.config.DatabaseConfig;
import com.example.service.FHIRImportService;
import com.example.util.JsonUtil;
import org.hl7.fhir.r4.model.DateTimeType;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Properties;

public class ManualTests {

  void testImport() throws Exception {
    DatabaseConfig dbConfig = new DatabaseConfig(new Properties());
    var importService = new FHIRImportService(dbConfig);
    File file = new File(ClassLoader.getSystemResource("fhir_sample.json").toURI());
    var jsonNode = JsonUtil.parseFile(file);
    try (var conn = dbConfig.getConnection()){
      importService.processBundle(jsonNode, conn);
    }
//    var result = importService.extractResourcesByType(jsonNode);
//    System.out.println(result.get("patient"));
  }

  void testDateTime() {
    System.out.println(new DateTimeType("1979-12-25"));
    System.out.println(new DateTimeType("1998-02-17T00:48:08+00:00"));
    System.out.println(new DateTimeType("hola"));
  }

  @Test @Disabled("manual")
  public void manualTest() throws Exception {
//    testDateTime();
    testImport();
  }
}
