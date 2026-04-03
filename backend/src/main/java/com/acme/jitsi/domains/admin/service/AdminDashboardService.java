package com.acme.jitsi.domains.admin.service;

import com.acme.jitsi.domains.admin.dto.AdminDashboardDrillDownResponse;
import com.acme.jitsi.domains.admin.dto.AdminDashboardSummaryResponse;
import com.acme.jitsi.domains.configsets.service.ConfigSet;
import com.acme.jitsi.domains.configsets.service.ConfigSetCompatibilityCheck;
import com.acme.jitsi.domains.configsets.service.ConfigSetCompatibilityStateService;
import com.acme.jitsi.domains.configsets.service.ConfigSetEnvironmentType;
import com.acme.jitsi.domains.configsets.service.ConfigSetRepository;
import com.acme.jitsi.domains.health.dto.HealthResponse;
import com.acme.jitsi.domains.health.dto.JoinReadinessResponse;
import com.acme.jitsi.domains.health.service.HealthService;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AdminDashboardService {

  private static final int SAMPLE_LIMIT = 500;

  private final HealthService healthService;
  private final ConfigSetRepository configSets;
  private final ConfigSetCompatibilityStateService compatState;
  private final AdminDashboardReadModel readModel;
  private final Clock clock;
  private final String traceUrlTemplate;
  private final AdminDashboardSummaryComposer summaryComposer;

  public AdminDashboardService(
      HealthService healthService,
      ConfigSetRepository configSets,
      ConfigSetCompatibilityStateService compatState,
      AdminDashboardReadModel readModel,
      Clock clock,
      @Value("${app.admin.trace-url-template:}") String traceUrlTemplate,
      @Value("${app.admin.incident-detail-url:}") String incidentDetailUrl) {
    this.healthService = healthService;
    this.configSets = configSets;
    this.compatState = compatState;
    this.readModel = readModel;
    this.clock = clock;
    this.traceUrlTemplate = traceUrlTemplate;
    this.summaryComposer = new AdminDashboardSummaryComposer(incidentDetailUrl);
  }

  public AdminDashboardSummaryResponse getSummary(
      String tenantId,
      String periodToken,
      String environmentToken,
      String roomId,
      String meetingId,
      String traceId) {
    AdminDashboardPeriod period = AdminDashboardPeriod.fromToken(periodToken);
    ConfigSetEnvironmentType environmentType = resolveEnvironment(tenantId, environmentToken);
    Instant generatedAt = Instant.now(clock);

    HealthResponse health = healthService.getHealth();
    JoinReadinessResponse joinReadiness = healthService.getJoinReadiness(traceId);
    ConfigSet activeConfigSet = resolveActiveConfigSet(tenantId, environmentType).orElse(null);
    ConfigSetCompatibilityCheck compatibility = activeConfigSet == null
        ? null
      : compatState.findLatestByConfigSetId(activeConfigSet.configSetId()).orElse(null);
    String environment = environmentType.name().toLowerCase(Locale.ROOT);

    AdminDashboardReadModel.JoinAuditOverview overview = readModel.loadJoinAuditOverview(new AdminDashboardReadModel.DashboardFilter(
        tenantId,
        environmentType,
        period.from(clock),
        roomId,
        meetingId,
      SAMPLE_LIMIT));

    return summaryComposer.compose(new AdminDashboardSummaryComposer.SummaryRequest(
        period.token(),
        environment,
        tenantId,
        generatedAt,
        traceId,
        health,
        joinReadiness,
        activeConfigSet,
        compatibility,
        overview,
        roomId,
        meetingId));
  }

  public AdminDashboardDrillDownResponse getDrillDown(
      String tenantId,
      String periodToken,
      String environmentToken,
      String roomId,
      String meetingId,
      String errorCode,
      String category) {
    AdminDashboardPeriod period = AdminDashboardPeriod.fromToken(periodToken);
    ConfigSetEnvironmentType environmentType = resolveEnvironment(tenantId, environmentToken);
    AdminDashboardReadModel.DrillDownOverview overview = readModel.loadDrillDown(new AdminDashboardReadModel.DrillDownFilter(
        tenantId,
        environmentType,
        period.from(clock),
        roomId,
        meetingId,
        normalizeOptional(errorCode),
        normalizeOptional(category),
      SAMPLE_LIMIT));

    return new AdminDashboardDrillDownResponse(
        period.token(),
        environmentType.name().toLowerCase(Locale.ROOT),
        tenantId,
        Instant.now(clock).toString(),
        overview.selectionType(),
        overview.selectionValue(),
        new AdminDashboardDrillDownResponse.EntityFilter(roomId, meetingId),
        overview.failureCount(),
        overview.recentFailures().stream()
            .map(record -> new AdminDashboardDrillDownResponse.RecentSample(
                record.occurredAt().toString(),
                record.roomId(),
                record.meetingId(),
                record.subjectId(),
                record.traceId(),
                buildTraceUrl(record.traceId()),
                record.errorCode(),
                record.reasonCategory(),
                record.userMessage()))
            .toList(),
        overview.sampleWindowLimited());
  }

  private String buildTraceUrl(String traceId) {
    String traceUrl = null;
    boolean hasTraceId = traceId != null && !traceId.isBlank();
    boolean hasTemplate = traceUrlTemplate != null && !traceUrlTemplate.isBlank();
    if (hasTraceId && hasTemplate) {
      traceUrl = traceUrlTemplate.replace("{traceId}", traceId);
    }
    return traceUrl;
  }

  private Optional<ConfigSet> resolveActiveConfigSet(String tenantId, ConfigSetEnvironmentType environmentType) {
    return configSets.findActiveByTenantIdAndEnvironmentType(tenantId, environmentType);
  }

  private ConfigSetEnvironmentType resolveEnvironment(String tenantId, String environmentToken) {
    ConfigSetEnvironmentType environmentType = ConfigSetEnvironmentType.DEV;
    if (environmentToken != null && !environmentToken.isBlank()) {
      try {
        environmentType = ConfigSetEnvironmentType.valueOf(environmentToken.trim().toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException ex) {
        throw new AdminDashboardInvalidRequestException(
            "Параметр environment должен быть одним из: %s."
                .formatted(Arrays.toString(ConfigSetEnvironmentType.values())),
            ex);
      }
    } else {
      for (ConfigSetEnvironmentType candidate : ConfigSetEnvironmentType.values()) {
        if (configSets.findActiveByTenantIdAndEnvironmentType(tenantId, candidate).isPresent()) {
          environmentType = candidate;
          break;
        }
      }
    }
    return environmentType;
  }

  private String normalizeOptional(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}