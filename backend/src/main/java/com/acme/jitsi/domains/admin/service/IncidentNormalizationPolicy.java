package com.acme.jitsi.domains.admin.service;

import java.util.Locale;

final class IncidentNormalizationPolicy {

  private IncidentNormalizationPolicy() {
  }

  static String normalizeCategory(String category, String errorCode) {
    if (hasText(category)) {
      return category.trim().toUpperCase(Locale.ROOT);
    }
    return hasText(errorCode) ? categoryForErrorCode(errorCode) : null;
  }

  static String normalizeErrorCode(String errorCode) {
    return hasText(errorCode) ? errorCode.trim().toUpperCase(Locale.ROOT) : null;
  }

  static String normalizeForKey(String value) {
    return hasText(value) ? value.trim() : "-";
  }

  static boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  static String blankToNull(String value) {
    return hasText(value) ? value.trim() : null;
  }

  static String firstNonBlank(String first, String second) {
    return hasText(first) ? first.trim() : blankToNull(second);
  }

  private static String categoryForErrorCode(String errorCode) {
    return switch (errorCode.trim().toUpperCase(Locale.ROOT)) {
      case "AUTH_REQUIRED" -> "SSO";
      case "TOKEN_INVALID", "TOKEN_REVOKED" -> "TOKEN";
      case "ROLE_MISMATCH", "ROLE_CONFLICT", "MEETING_ROLE_CONFLICT" -> "ROLE";
      case "CONFIG_INCOMPATIBLE" -> "CONFIG";
      default -> "NETWORK";
    };
  }
}