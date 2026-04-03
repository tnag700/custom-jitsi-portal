package com.acme.jitsi.domains.admin.service;

import com.acme.jitsi.domains.admin.dto.AdminRoleHistoryResponse;
import com.acme.jitsi.domains.configsets.service.ConfigSetEnvironmentType;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

@Service
public class AdminRoleHistoryService {

  private static final int DEFAULT_PAGE_SIZE = 20;
  private static final int MAX_PAGE_SIZE = 100;
  private static final List<String> ACTIONS = List.of("assign", "update", "unassign");

  private final AdminRoleHistoryReadModel readModel;
  private final Clock clock;
  private final AdminRoleHistoryEntryMapper entryMapper;

  public AdminRoleHistoryService(AdminRoleHistoryReadModel readModel, Clock clock) {
    this.readModel = readModel;
    this.clock = clock;
    this.entryMapper = new AdminRoleHistoryEntryMapper();
  }

  public AdminRoleHistoryResponse getRoleHistory(
      String tenantId,
      Collection<String> authorities,
      AdminRoleHistoryQuery query) {
    ConfigSetEnvironmentType environmentType = resolveEnvironment(query.environment());
    int pageSize = clampPageSize(query.pageSize());
    int page = Math.max(query.page(), 0);
    String normalizedAction = normalizeAction(query.actionType());
    Instant from = parseInstant(query.from(), Instant.now(clock).minusSeconds(24 * 60 * 60L));
    Instant to = parseInstant(query.to(), Instant.now(clock));

    if (!hasPrimarySelector(query)) {
      throw new AdminRoleHistoryInvalidRequestException(
          "Нужен хотя бы один primary filter: q, subjectId, roomId или meetingId.");
    }
    if (to.isBefore(from)) {
      throw new AdminRoleHistoryInvalidRequestException("Параметр to не может быть раньше from.");
    }

    AdminRoleHistoryReadModel.PageResult pageResult = readModel.loadHistory(new AdminRoleHistoryReadModel.Filter(
        tenantId,
        environmentType,
        normalizeOptional(query.query()),
        normalizedAction,
        normalizeOptional(query.role()),
        normalizeOptional(query.actorId()),
        normalizeOptional(query.subjectId()),
        normalizeOptional(query.roomId()),
        normalizeOptional(query.meetingId()),
        normalizeOptional(query.traceId()),
        from,
        to,
        page,
        pageSize));

    boolean fullReferenceAllowed = entryMapper.canViewFullReference(authorities);
    int totalPages = pageResult.totalElements() == 0
        ? 0
        : (int) Math.ceil((double) pageResult.totalElements() / (double) pageSize);

    return new AdminRoleHistoryResponse(
        tenantId,
        environmentLabel(environmentType),
        Instant.now(clock).toString(),
        page,
        pageSize,
        pageResult.totalElements(),
        totalPages,
        pageResult.rows().stream().map(row -> entryMapper.toEntry(row, fullReferenceAllowed)).toList());
  }

  private boolean hasPrimarySelector(AdminRoleHistoryQuery query) {
    return hasText(query.query())
        || hasText(query.subjectId())
        || hasText(query.roomId())
        || hasText(query.meetingId());
  }

  private ConfigSetEnvironmentType resolveEnvironment(@Nullable String token) {
    if (!hasText(token)) {
      return null;
    }
    try {
      return ConfigSetEnvironmentType.valueOf(token.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException ex) {
      throw new AdminRoleHistoryInvalidRequestException(
          "Параметр environment должен быть одним из: %s.".formatted(List.of(ConfigSetEnvironmentType.values())),
          ex);
    }
  }

  private @Nullable String normalizeAction(@Nullable String actionType) {
    String normalized = normalizeOptional(actionType);
    if (normalized == null) {
      return null;
    }
    String candidate = normalized.toLowerCase(Locale.ROOT);
    if (!ACTIONS.contains(candidate)) {
      throw new AdminRoleHistoryInvalidRequestException(
          "Параметр actionType должен быть одним из: %s.".formatted(ACTIONS));
    }
    return candidate;
  }

  private Instant parseInstant(@Nullable String value, Instant fallback) {
    if (!hasText(value)) {
      return fallback;
    }
    try {
      return Instant.parse(value.trim());
    } catch (RuntimeException ex) {
      throw new AdminRoleHistoryInvalidRequestException(
          "Параметры from/to должны быть в формате ISO-8601 UTC.",
          ex);
    }
  }

  private int clampPageSize(int pageSize) {
    if (pageSize <= 0) {
      return DEFAULT_PAGE_SIZE;
    }
    return Math.min(pageSize, MAX_PAGE_SIZE);
  }

  private String environmentLabel(@Nullable ConfigSetEnvironmentType environmentType) {
    return environmentType == null ? "all" : environmentType.name().toLowerCase(Locale.ROOT);
  }

  private @Nullable String normalizeOptional(@Nullable String value) {
    return hasText(value) ? value.trim() : null;
  }

  private boolean hasText(@Nullable String value) {
    return value != null && !value.isBlank();
  }

  public record AdminRoleHistoryQuery(
      @Nullable String environment,
      @Nullable String query,
      @Nullable String from,
      @Nullable String to,
      @Nullable String actionType,
      @Nullable String role,
      @Nullable String actorId,
      @Nullable String subjectId,
      @Nullable String roomId,
      @Nullable String meetingId,
      @Nullable String traceId,
      int page,
      int pageSize) {
  }
}