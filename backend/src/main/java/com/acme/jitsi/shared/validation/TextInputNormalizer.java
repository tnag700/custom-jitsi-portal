package com.acme.jitsi.shared.validation;

public final class TextInputNormalizer {

  private TextInputNormalizer() {
  }

  public static String normalizeRequired(String value) {
    if (value == null) {
      return null;
    }
    return value.trim();
  }

  public static String normalizeOptional(String value) {
    if (value == null) {
      return null;
    }
    String normalized = value.trim();
    return normalized.isEmpty() ? null : normalized;
  }

  public static String normalizeNullable(String value) {
    if (value == null) {
      return null;
    }
    return value.trim();
  }

  public static boolean containsControlCharacters(String value) {
    if (value == null) {
      return false;
    }
    for (int index = 0; index < value.length(); index++) {
      if (Character.isISOControl(value.charAt(index))) {
        return true;
      }
    }
    return false;
  }
}