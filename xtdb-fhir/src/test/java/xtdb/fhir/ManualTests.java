package xtdb.fhir;

import xtdb.fhir.util.JsonUtil;
import org.hl7.fhir.r4.model.DateTimeType;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import javax.sql.DataSource;
import java.io.File;

@Disabled
@ExtendWith(XtdbPlayground.class)
public class ManualTests {

  @Test
  void testImport(DataSource dataSource) throws Exception {
    var importService = new FHIRImportService(new JdbcResourceSink(dataSource, new XtdbRecordsWriter(), 1));
    File file = new File(ClassLoader.getSystemResource("fhir_sample.json").toURI());
    var jsonNode = JsonUtil.parseFile(file);
    importService.processBundle(jsonNode);

    try (var conn = dataSource.getConnection()){
      try (var stmt = conn.createStatement(); var resultSet = stmt.executeQuery("SELECT * FROM patient")) {
        var md = resultSet.getMetaData();
        int cols = md.getColumnCount();
        while (resultSet.next()) {
          for (int i = 1; i <= cols; i++) {
            System.out.println(md.getColumnLabel(i) + "=" + resultSet.getString(i));
          }
        }
      }
    }
//    var result = importService.extractResourcesByType(jsonNode);
//    System.out.println(result.get("patient"));
  }

  @Test
  void testDateTime() {
    System.out.println(new DateTimeType("1979-12-25"));
    System.out.println(new DateTimeType("1998-02-17T00:48:08+00:00"));
    System.out.println(new DateTimeType("hola"));
  }

}
