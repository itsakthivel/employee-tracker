package com.example.tracker.common;

import java.util.regex.Pattern;

/** Shared field rules from data-model.md. */
public final class Validators {

  public static final Pattern EMPLOYEE_NUMBER = Pattern.compile("^[A-Za-z0-9-]{1,20}$");
  public static final Pattern EMAIL =
      Pattern.compile("^[A-Za-z0-9._%+'-]+@[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)*\\.[A-Za-z]{2,}$");
  public static final Pattern PHONE = Pattern.compile("^\\+\\d{7,15}$");

  private Validators() {}

  /** Removes spaces, dashes, dots and brackets accepted on input; returns null for blank. */
  public static String normalizePhone(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    return raw.replaceAll("[\\s\\-().]", "");
  }

  public static String trimToNull(String s) {
    if (s == null) {
      return null;
    }
    String t = s.trim();
    return t.isEmpty() ? null : t;
  }

  public static void requireLength(
      FieldErrors errors, String field, String value, int min, int max, boolean required) {
    if (value == null) {
      if (required) {
        errors.add(field, "is required");
      }
      return;
    }
    if (value.length() < min) {
      errors.add(field, min <= 1 ? "is required" : "must be at least " + min + " characters");
    } else if (value.length() > max) {
      errors.add(field, "must be at most " + max + " characters");
    }
  }
}
