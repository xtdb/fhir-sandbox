package com.example;

import java.lang.reflect.InvocationTargetException;
import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.example.util.TemporalParser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class TempParsTest {

  // Assert that the simple date is parsed correctly
  @Test
  public void dateParse() {
    String input = "1985-03-15";
    LocalDate result = TemporalParser.parseLocalDate(input);
    assertThat(LocalDate.of(1985, 3, 15)).isEqualTo(result);
  }

  // Assert that null or empty string returns null
  @Test
  public void nullDateOrEmptyString() {
    LocalDate result = TemporalParser.parseLocalDate(null);
    assertThat(result).isNull();

    result = TemporalParser.parseLocalDate("");
    assertThat(result).isNull();

    result = TemporalParser.parseLocalDate("          ");
    assertThat(result).isNull();
  }

  // Assert that the date is extracted correctly
  @Test
  public void extractDate() {
    String input = "1985-03-15T10:30:00Z";
    LocalDate result = TemporalParser.parseLocalDate(input);

    assertThat(LocalDate.of(1985, 3, 15)).isEqualTo(result);
  }

  // Assert that invalid date returns null
  @Test
  public void invalidDate() {
    LocalDate result = TemporalParser.parseLocalDate("not-a-date-time-:)");
    assertThat(result).isNull();
  }

  // Assert that the instant is parsed correctly
  @Test
  public void extractInstant() {
    String input = "2019-03-15T09:00:00Z";

    Instant result = TemporalParser.parseInstant(input);

    assertThat(result).isNotNull();
    assertThat(result).isEqualTo(Instant.parse("2019-03-15T09:00:00Z"));
  }

  // Assert that the date is parsed correctly with timezone offset
  @Test
  public void extractDateWithTimezoneOffset() {
    String input = "2019-03-15T09:00:00+01:00";
    LocalDate result = TemporalParser.parseLocalDate(input);

    assertThat(result).isEqualTo(LocalDate.of(2019, 3, 15));
  }

  // Assert that invalid date formats return null
  @Test
  public void invalidDateFormats() {
    assertThat(TemporalParser.parseLocalDate("2019")).isNull();
    assertThat(TemporalParser.parseLocalDate("2019-13-01")).isNull();
    assertThat(TemporalParser.parseLocalDate("abc123")).isNull();
  }

  // Assert that the instant is parsed correctly with timezone offset
  @Test
  public void parseInstantWithTimezoneOffset() {
    // 09:00+01:00 = 08:00 UTC
    Instant result = TemporalParser.parseInstant("2019-03-15T09:00:00+01:00");

    assertThat(result).isNotNull();
    assertThat(result).isEqualTo(Instant.parse("2019-03-15T08:00:00Z"));
  }

  // Assert that the instant is parsed correctly with milliseconds
  @Test
  public void parseInstantWithMilliseconds() {
    Instant result = TemporalParser.parseInstant("2019-03-15T09:00:00.123Z");

    assertThat(result).isNotNull();
  }

  // Assert that the instant is parsed correctly with date only
  @Test
  public void parseInstantWithDateOnly() {
    Instant result = TemporalParser.parseInstant("2019-03-15");

    assertThat(result).isNotNull();
    assertThat(result).isEqualTo(Instant.parse("2019-03-15T00:00:00Z"));
  }
  
  // Assert that null or empty string returns null for parseInstant
  @Test
  public void nullOrEmptyInstant() {
    assertThat(TemporalParser.parseInstant(null)).isNull();
    assertThat(TemporalParser.parseInstant("")).isNull();
    assertThat(TemporalParser.parseInstant("   ")).isNull();
    assertThat(TemporalParser.parseInstant("\t")).isNull();
  }

  // Assert that invalid instant formats return null
  @Test
  public void invalidInstantFormats() {
    assertThat(TemporalParser.parseInstant("not-a-date")).isNull();
    assertThat(TemporalParser.parseInstant("abc123")).isNull();
  }

  // Assert that the instant is parsed correctly from a LocalDate
  @Test
  public void toInstantFromLocalDate() {
    LocalDate date = LocalDate.of(2019, 3, 15);

    Instant result = TemporalParser.toInstant(date);

    assertThat(result).isEqualTo(Instant.parse("2019-03-15T00:00:00Z"));
  }

  // Assert that null returns null for toInstant
  @Test
  public void toInstantFromNull() {
    assertThat(TemporalParser.toInstant(null)).isNull();
  }

  // Assert that singleton constructor throws AssertionError
  @Test
  public void constructorThrowsAssertionError() throws Exception {
    var constructor = TemporalParser.class.getDeclaredConstructor();
    constructor.setAccessible(true);

    assertThatThrownBy(constructor::newInstance)
        .isInstanceOf(InvocationTargetException.class)
        .cause()
        .isInstanceOf(AssertionError.class)
        .hasMessage("Utility class: do not instantiate");
  }
}