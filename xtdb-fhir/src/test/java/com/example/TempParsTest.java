package com.example;

import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.example.util.TemporalParser;

import static org.assertj.core.api.Assertions.assertThat;
// import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class TempParsTest {

  //
  @Test
  public void basicParse() {
    String input = "1985-03-15";
    LocalDate result = TemporalParser.parseLocalDate(input);
    assertThat(LocalDate.of(1985, 3, 15)).isEqualTo(result);
  }

  @Test
  public void nullDateOrEmptyString() {
    LocalDate result = TemporalParser.parseLocalDate(null);
    assertThat(result).isNull();

    result = TemporalParser.parseLocalDate("");
    assertThat(result).isNull();

    result = TemporalParser.parseLocalDate("          ");
    assertThat(result).isNull();
  }

  @Test
  public void extractDate() {
    String input = "1985-03-15T10:30:00Z";
    LocalDate result = TemporalParser.parseLocalDate(input);

    assertThat(LocalDate.of(1985, 3, 15)).isEqualTo(result);
  }

  @Test
  public void invalidDate() {
    LocalDate result = TemporalParser.parseLocalDate("not-a-date-time-:)");
    assertThat(result).isNull();
  }

  @Test
  public void extractInstant() {
    String input = "2019-03-15T09:00:00Z";
    
    Instant result = TemporalParser.parseInstant(input);
    
    assertThat(result).isNotNull();
    assertThat(result).isEqualTo(Instant.parse("2019-03-15T09:00:00Z"));
  }
}