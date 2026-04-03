package com.acme.jitsi.domains.admin.service;

import com.acme.jitsi.domains.configsets.service.ConfigSetEnvironmentType;
import java.util.List;
import java.util.Locale;

final class IncidentEnvironmentPolicy {

  private IncidentEnvironmentPolicy() {
  }

  static String environmentLabel(ConfigSetEnvironmentType environmentType) {
    return environmentType == null ? "all" : environmentType.name().toLowerCase(Locale.ROOT);
  }

  static ConfigSetEnvironmentType resolveEnvironment(String environmentToken) {
    if (!IncidentNormalizationPolicy.hasText(environmentToken)) {
      return null;
    }
    try {
      return ConfigSetEnvironmentType.valueOf(environmentToken.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException ex) {
      throw new AdminIncidentsInvalidRequestException(
          "Параметр environment должен быть одним из: %s.".formatted(List.of(ConfigSetEnvironmentType.values())),
          ex);
    }
  }
}