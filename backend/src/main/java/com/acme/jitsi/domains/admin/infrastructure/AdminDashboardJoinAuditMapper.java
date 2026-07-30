package com.acme.jitsi.domains.admin.infrastructure;

import com.acme.jitsi.domains.admin.service.AdminDashboardReadModel;
import com.acme.jitsi.domains.admin.service.AdminDashboardReasonCategory;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

final class AdminDashboardJoinAuditMapper {

  private static final String SUCCESS_MESSAGE = "Вход во встречу выполнен успешно.";
  private static final String AUTH_MESSAGE = "Не удалось выпустить токен доступа. Выполните вход через SSO повторно.";
  private static final String CONFIG_MESSAGE = "Активный конфиг-контур несовместим с требованиями входа во встречу.";
  private static final String ROLE_MESSAGE = "Назначенная роль не прошла проверку для входа во встречу.";
  private static final Map<String, String> ERROR_MESSAGES = Map.ofEntries(
      Map.entry("AUTH_REQUIRED", AUTH_MESSAGE),
      Map.entry("TOKEN_INVALID", AUTH_MESSAGE),
      Map.entry("TOKEN_REVOKED", AUTH_MESSAGE),
      Map.entry("CONFIG_INCOMPATIBLE", CONFIG_MESSAGE),
      Map.entry("ROLE_MISMATCH", ROLE_MESSAGE),
      Map.entry("ROLE_CONFLICT", ROLE_MESSAGE),
      Map.entry("MEETING_ROLE_CONFLICT", ROLE_MESSAGE));

  AdminDashboardJoinAuditMapper() {
  }

  AdminDashboardReadModel.JoinAuditRecord toRecord(Object[] row) {
    Instant occurredAt = toInstant(row[0]);
    String roomId = value(row[1]);
    String meetingId = value(row[2]);
    String subjectId = value(row[3]);
    String traceId = value(row[4]);
    String actionType = value(row[5]);
    Map<String, String> changedFields = parseChangedFields(value(row[6]));
    String errorCode = null;
    String reasonCategory = null;
    if ("join_failed".equalsIgnoreCase(actionType)) {
      errorCode = blankToNull(changedFields.get("errorCode"));
      reasonCategory = blankToNull(changedFields.get("reasonCategory"));
    }
    return new AdminDashboardReadModel.JoinAuditRecord(
        occurredAt,
        roomId,
        meetingId,
        subjectId,
        traceId,
        errorCode,
        reasonCategory,
        buildUserMessage(errorCode, reasonCategory));
  }

  String toDashboardReasonCategory(String reasonCategory) {
    if (reasonCategory == null || reasonCategory.isBlank()) {
      return null;
    }
    return AdminDashboardReasonCategory.fromToken(reasonCategory)
        .map(AdminDashboardReasonCategory::token)
        .orElse(null);
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
      String key = fragment.substring(0, separator).trim();
      String value = fragment.substring(separator + 1).trim();
      values.put(key, value);
    }
    return values;
  }

  private String buildUserMessage(String errorCode, String reasonCategory) {
    if (errorCode == null || errorCode.isBlank()) {
      return SUCCESS_MESSAGE;
    }
    String normalizedErrorCode = normalize(errorCode, "UNKNOWN").toUpperCase(Locale.ROOT);
    String mappedMessage = ERROR_MESSAGES.get(normalizedErrorCode);
    if (mappedMessage != null) {
      return mappedMessage;
    }
    return "Вход во встречу завершился ошибкой категории %s.".formatted(normalize(reasonCategory, "UNKNOWN"));
  }

  private String normalize(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value;
  }

  private String blankToNull(String value) {
    return value == null || value.isBlank() || "null".equalsIgnoreCase(value) ? null : value;
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
