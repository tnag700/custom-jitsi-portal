package com.acme.jitsi.domains.admin.service;

import java.util.Locale;
import java.util.Optional;

public enum AdminDashboardReasonCategory {
  SSO,
  TOKEN,
  ROLE,
  NETWORK,
  MEDIA,
  CONFIG;

  public static Optional<AdminDashboardReasonCategory> fromToken(String value) {
    if (value == null || value.isBlank()) {
      return Optional.empty();
    }

    try {
      return Optional.of(valueOf(value.trim().toUpperCase(Locale.ROOT)));
    } catch (IllegalArgumentException exception) {
      return Optional.empty();
    }
  }

  public String token() {
    return name();
  }
}
