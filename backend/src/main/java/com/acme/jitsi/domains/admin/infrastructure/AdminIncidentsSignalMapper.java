package com.acme.jitsi.domains.admin.infrastructure;

import com.acme.jitsi.domains.admin.service.AdminIncidentsReadModel;
import com.acme.jitsi.domains.configsets.service.ConfigSetEnvironmentType;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

final class AdminIncidentsSignalMapper {

  AdminIncidentsReadModel.IncidentSignal toMeetingSignal(Object[] row) {
    Map<String, String> changedFields = parseChangedFields(value(row[7]));
    String errorCode = changedFields.get("errorCode");
    String category = changedFields.get("reasonCategory");
    return new AdminIncidentsReadModel.IncidentSignal(
        toInstant(row[0]),
        value(row[1]),
        toEnvironment(row[2]),
        value(row[3]),
        value(row[4]),
        value(row[5]),
        value(row[6]),
        value(row[6]),
        errorCode,
        category,
        changedFields.get("role"),
        firstNonBlank(changedFields.get("diagnosticResult"), buildDiagnosticMessage(errorCode, category)),
        changedFields.get("alertSeverity"),
        changedFields.get("joinReadiness"));
  }

  AdminIncidentsReadModel.IncidentSignal toAuthSignal(Object[] row) {
    String errorCode = value(row[7]);
    String category = mapAuthCategory(errorCode, value(row[9]));
    return new AdminIncidentsReadModel.IncidentSignal(
        toInstant(row[0]),
        value(row[1]),
        toEnvironment(row[2]),
        value(row[3]),
        value(row[4]),
        value(row[5]),
        value(row[6]),
        value(row[6]),
        errorCode,
        category,
        null,
        firstNonBlank(value(row[8]), buildDiagnosticMessage(errorCode, category)),
        null,
        null);
  }

  AdminIncidentsReadModel.IncidentSignal toConfigSignal(Object[] row) {
    return new AdminIncidentsReadModel.IncidentSignal(
        toInstant(row[0]),
        value(row[1]),
        toEnvironment(row[2]),
        null,
        null,
        null,
        value(row[3]),
        value(row[3]),
        "CONFIG_INCOMPATIBLE",
        "CONFIG",
        null,
        firstNonBlank(value(row[4]), "Активный конфиг-контур несовместим с требованиями входа."),
        "critical",
        "blocked");
  }

  private String mapAuthCategory(String errorCode, String eventType) {
    String normalized = errorCode == null ? "" : errorCode.trim().toUpperCase(Locale.ROOT);
    if (normalized.startsWith("TOKEN_")) {
      return "TOKEN";
    }
    if ("AUTH_REQUIRED".equals(normalized) || "SSO_LOGIN_FAILED".equalsIgnoreCase(eventType)) {
      return "SSO";
    }
    if ("CONFIG_INCOMPATIBLE".equals(normalized)) {
      return "CONFIG";
    }
    if ("ROLE_MISMATCH".equals(normalized)
        || "ROLE_CONFLICT".equals(normalized)
        || "MEETING_ROLE_CONFLICT".equals(normalized)) {
      return "ROLE";
    }
    return "NETWORK";
  }

  private Map<String, String> parseChangedFields(String changedFields) {
    Map<String, String> values = new LinkedHashMap<>();
    if (changedFields == null || changedFields.isBlank()) {
      return values;
    }
    for (String fragment : changedFields.split(",")) {
      int separator = fragment.indexOf('=');
      if (separator < 0) {
        continue;
      }
      values.put(fragment.substring(0, separator).trim(), fragment.substring(separator + 1).trim());
    }
    return values;
  }

  private String buildDiagnosticMessage(String errorCode, String category) {
    if (errorCode == null || errorCode.isBlank()) {
      return "Сигнал инцидента без детализации.";
    }
    return switch (errorCode.trim().toUpperCase(Locale.ROOT)) {
      case "AUTH_REQUIRED", "TOKEN_INVALID", "TOKEN_REVOKED" -> "Не удалось завершить вход. Повторите SSO-authentication и проверьте токен.";
      case "CONFIG_INCOMPATIBLE" -> "Активный конфиг-контур несовместим с требованиями входа.";
      case "ROLE_MISMATCH", "ROLE_CONFLICT", "MEETING_ROLE_CONFLICT" -> "Назначенная роль не прошла проверку для входа.";
      default -> "Инцидент категории %s требует ручного расследования.".formatted(category == null ? "UNKNOWN" : category);
    };
  }

  private ConfigSetEnvironmentType toEnvironment(Object raw) {
    if (raw == null) {
      return ConfigSetEnvironmentType.DEV;
    }
    return ConfigSetEnvironmentType.valueOf(raw.toString());
  }

  private String firstNonBlank(String first, String second) {
    return first != null && !first.isBlank() ? first : second;
  }

  private String value(Object raw) {
    return raw == null ? null : raw.toString();
  }

  private Instant toInstant(Object raw) {
    if (raw instanceof Instant instant) {
      return instant;
    }
    if (raw instanceof Timestamp timestamp) {
      return timestamp.toInstant();
    }
    if (raw instanceof OffsetDateTime offsetDateTime) {
      return offsetDateTime.toInstant();
    }
    return Instant.parse(raw.toString());
  }
}