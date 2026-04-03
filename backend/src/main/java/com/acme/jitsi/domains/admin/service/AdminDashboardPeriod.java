package com.acme.jitsi.domains.admin.service;

import java.time.Clock;
import java.time.Instant;

public enum AdminDashboardPeriod {
  FIFTEEN_MINUTES("15m", 15 * 60L),
  ONE_HOUR("1h", 60 * 60L),
  TWENTY_FOUR_HOURS("24h", 24 * 60 * 60L);

  private final String token;
  private final long seconds;

  AdminDashboardPeriod(String token, long seconds) {
    this.token = token;
    this.seconds = seconds;
  }

  public String token() {
    return token;
  }

  public Instant from(Clock clock) {
    return Instant.now(clock).minusSeconds(seconds);
  }

  public static AdminDashboardPeriod fromToken(String value) {
    if (value == null || value.isBlank()) {
      return FIFTEEN_MINUTES;
    }
    for (AdminDashboardPeriod candidate : values()) {
      if (candidate.token.equalsIgnoreCase(value.trim())) {
        return candidate;
      }
    }
    return FIFTEEN_MINUTES;
  }
}