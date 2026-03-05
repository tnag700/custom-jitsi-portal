package com.acme.jitsi.domains.store;

import java.util.Locale;

public enum StoreMode {
  REDIS,
  IN_MEMORY;

  public static StoreMode fromRaw(String rawMode, StoreMode defaultMode) {
    if (rawMode == null || rawMode.isBlank()) {
      return defaultMode;
    }

    return switch (rawMode.trim().toLowerCase(Locale.ROOT)) {
      case "redis" -> REDIS;
      case "in-memory", "in_memory" -> IN_MEMORY;
      default -> IN_MEMORY;
    };
  }
}