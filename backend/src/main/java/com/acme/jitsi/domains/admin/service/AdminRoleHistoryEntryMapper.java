package com.acme.jitsi.domains.admin.service;

import com.acme.jitsi.domains.admin.dto.AdminRoleHistoryResponse;
import java.util.Collection;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

final class AdminRoleHistoryEntryMapper {

  private static final Set<String> FULL_REFERENCE_AUTHORITIES = Set.of(
      "role_security-admin",
      "security-admin",
      "role_system-admin",
      "system-admin");

  boolean canViewFullReference(@Nullable Collection<String> authorities) {
    if (authorities == null) {
      return false;
    }
    return authorities.stream()
        .filter(Objects::nonNull)
        .map(value -> value.toLowerCase(Locale.ROOT))
        .anyMatch(FULL_REFERENCE_AUTHORITIES::contains);
  }

  AdminRoleHistoryResponse.RoleHistoryEntry toEntry(
      AdminRoleHistoryReadModel.RoleHistoryRow row,
      boolean fullReferenceAllowed) {
    String subjectReference = redactReference(row.subjectId(), fullReferenceAllowed);
    String actorReference = redactReference(row.actorId(), fullReferenceAllowed);
    return new AdminRoleHistoryResponse.RoleHistoryEntry(
        row.occurredAt().toString(),
        row.actionType(),
        actionLabel(row.actionType()),
        row.oldRole(),
        row.newRole(),
        preferredLabel(row.subjectFullName(), subjectReference),
        subjectReference,
        preferredLabel(row.actorFullName(), actorReference),
        actorReference,
        row.tenantId(),
        row.environmentType().name().toLowerCase(Locale.ROOT),
        row.roomId(),
        row.meetingId(),
        row.traceId());
  }

  private String actionLabel(String actionType) {
    return switch (actionType) {
      case "assign" -> "Назначение";
      case "update" -> "Изменение роли";
      case "unassign" -> "Отзыв назначения";
      default -> "Изменение роли";
    };
  }

  private String preferredLabel(@Nullable String fullName, @Nullable String reference) {
    if (hasText(fullName)) {
      return fullName.trim();
    }
    return reference;
  }

  private String redactReference(@Nullable String value, boolean fullReferenceAllowed) {
    if (!hasText(value)) {
      return null;
    }
    String trimmed = value.trim();
    if (fullReferenceAllowed) {
      return trimmed;
    }
    if (trimmed.length() <= 4) {
      return "***";
    }
    return trimmed.substring(0, 3) + "***" + trimmed.substring(trimmed.length() - 2);
  }

  private boolean hasText(@Nullable String value) {
    return value != null && !value.isBlank();
  }
}