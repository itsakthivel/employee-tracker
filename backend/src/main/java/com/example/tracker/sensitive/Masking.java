package com.example.tracker.sensitive;

/** FR-006c: only the last 4 characters are ever shown without a reveal. */
public final class Masking {

  public static final String DOTS = "••••";

  private Masking() {}

  public static String last4(String plain) {
    if (plain == null) {
      return null;
    }
    return plain.length() <= 4 ? plain : plain.substring(plain.length() - 4);
  }

  public static String mask(String last4) {
    return last4 == null ? null : DOTS + last4;
  }

  public static String maskPresent(boolean present) {
    return present ? DOTS : null;
  }
}
