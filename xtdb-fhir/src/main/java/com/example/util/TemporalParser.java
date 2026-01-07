package com.example.util;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class TemporalParser {
    private static final Logger logger = LoggerFactory.getLogger(TemporalParser.class);

  public static LocalDate parseLocalDate(String dateString) {
    if (dateString == null || dateString.isBlank()) {
      return null;
    }

    try {
      dateString = dateString.length() > 10 ? dateString.substring(0, 10) : dateString;
      return LocalDate.parse(dateString);
      
    } catch (DateTimeParseException e) {
      logger.warn("Could not parse date: '{}' - {}", dateString, e.getMessage());
      return null;
    } 
  }

    public static Instant parseInstant(String dateTimeString) {
    if (dateTimeString == null || dateTimeString.isBlank()) {
      return null;
    }

    try {
      return Instant.parse(dateTimeString);
    } catch (DateTimeParseException e) {
      logger.warn("Could not parse instant: '{}' - {}", dateTimeString, e.getMessage());
      return null;
    } 
  }
}
