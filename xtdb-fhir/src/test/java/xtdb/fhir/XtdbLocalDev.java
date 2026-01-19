package xtdb.fhir;

import org.junit.jupiter.api.extension.*;
import org.postgresql.ds.PGSimpleDataSource;

import javax.sql.DataSource;

@SuppressWarnings({"NullableProblems", "unused"})
public class XtdbLocalDev implements BeforeEachCallback, ParameterResolver {

  public DataSource dataSource;

  @Override
  public void beforeEach(ExtensionContext context) {
    var dataSource = new PGSimpleDataSource();
    dataSource.setServerNames(new String[] {"localhost"});
    dataSource.setPortNumbers(new int[] {5434});
    dataSource.setDatabaseName("xtdb");
    this.dataSource = dataSource;
  }

  @Override
  public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
    return parameterContext.getParameter().getType() == DataSource.class;
  }

  @Override
  public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
    return dataSource;
  }
}
