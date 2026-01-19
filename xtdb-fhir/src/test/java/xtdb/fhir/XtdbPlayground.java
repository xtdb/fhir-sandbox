package xtdb.fhir;

import org.junit.jupiter.api.extension.*;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.GenericContainer;

import javax.sql.DataSource;
import java.time.Duration;
import java.util.UUID;

@SuppressWarnings("NullableProblems")
public class XtdbPlayground implements BeforeAllCallback, BeforeEachCallback, ParameterResolver {

  public final GenericContainer<?> xtdbContainer;
  public DataSource dataSource;

  @SuppressWarnings("resource")
  public XtdbPlayground() {
    xtdbContainer = new GenericContainer<>("ghcr.io/xtdb/xtdb:nightly")
        .withEnv("XTDB_LOGGING_LEVEL_PGWIRE", "debug")
        .withEnv("XTDB_LOGGING_LEVEL_SQL", "debug")
        .withCommand("playground")
        .withExposedPorts(5432)
        .withStartupTimeout(Duration.ofSeconds(10))
        .withReuse(true);
  }

  @Override
  public void beforeAll(ExtensionContext context) {
    xtdbContainer.start();
  }

  @Override
  public void beforeEach(ExtensionContext context) {
    var dataSource = new PGSimpleDataSource();
    dataSource.setServerNames(new String[] {xtdbContainer.getHost()});
    dataSource.setPortNumbers(new int[] { xtdbContainer.getMappedPort(5432) });
    dataSource.setDatabaseName(UUID.randomUUID().toString());
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
