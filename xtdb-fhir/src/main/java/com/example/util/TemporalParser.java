package com.example.util;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class TemporalParser {
  // Logger for logging warnings
  private static final Logger logger = LoggerFactory.getLogger(TemporalParser.class);

  // Private constructor to prevent instantiation
  private TemporalParser() {
    throw new AssertionError("Utility class: do not instantiate");
  }

  /**
   * Parse a date string into a LocalDate
   * 
   * @param dateString The date string to parse
   * @return The parsed LocalDate, or null if the string is null or blank
   */
  public static LocalDate parseLocalDate(String dateString) {
    if (dateString == null || dateString.isBlank()) {
      return null;
    }

    try {
      // If the string is longer than 10 characters, truncate it to the first 10 (as they are the only relevant ones)
      dateString = dateString.length() > 10 ? dateString.substring(0, 10) : dateString;
      return LocalDate.parse(dateString);
      
    } catch (DateTimeParseException e) {
      logger.warn("Could not parse date: '{}' - {}", dateString, e.getMessage());
      return null;
    } 
  }

    /**
     * Parse a date-time string into an Instant
     * 
     * @param dateTimeString The date-time string to parse
     * @return The parsed Instant, or null if the string is null or blank
     */
    public static Instant parseInstant(String dateTimeString) {
    if (dateTimeString == null || dateTimeString.isBlank()) {
      return null;
    }

    try {
      return Instant.parse(dateTimeString);
    } catch (DateTimeParseException e) {
      // Test if the string is actually a date, if so, convert it to an instant
      LocalDate date = TemporalParser.parseLocalDate(dateTimeString);
      logger.warn("Could not parse instant: '{}' - {}, will try to parse as date", dateTimeString, e.getMessage());
      return  date == null ? null : date.atStartOfDay(ZoneOffset.UTC).toInstant();
    } 
  }

  /**
   * Convert a LocalDate to an Instant
   * 
   * @param date The LocalDate to convert
   * @return The parsed Instant, or null if the date is null
   */
  public static Instant toInstant(LocalDate date) {
    if (date == null) {
      return null;
    }
    return date.atStartOfDay(ZoneOffset.UTC).toInstant();
  }
}
