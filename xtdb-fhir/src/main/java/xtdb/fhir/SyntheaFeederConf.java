package xtdb.fhir;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.jmx.export.annotation.ManagedAttribute;
import org.springframework.jmx.export.annotation.ManagedResource;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "synthea-feeder")
@ManagedResource
public class SyntheaFeederConf {
  private int intervalSeconds = 10;

  @ManagedAttribute
  public int getIntervalSeconds() {
    return intervalSeconds;
  }

  @ManagedAttribute
  public void setIntervalSeconds(int intervalSeconds) {
    this.intervalSeconds = intervalSeconds;
  }
}
