package com.acme.jitsi.domains.health.service;

import com.acme.jitsi.domains.configsets.service.ConfigSetCompatibilityCheck;
import com.acme.jitsi.domains.configsets.service.ConfigSetCompatibilityStateService;
import com.acme.jitsi.domains.health.dto.HealthResponse;
import com.acme.jitsi.domains.health.dto.JoinReadinessCheckResponse;
import com.acme.jitsi.domains.health.dto.JoinReadinessResponse;
import com.acme.jitsi.domains.meetings.service.MeetingJoinConfigurationReadiness;
import com.acme.jitsi.domains.meetings.service.MeetingJoinConfigurationReadinessService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class DefaultHealthService implements HealthService {

  private final ConfigSetCompatibilityStateService compatibilityStateService;
  private final MeetingJoinConfigurationReadinessService meetingJoinConfigurationReadinessService;

  public DefaultHealthService(
      ConfigSetCompatibilityStateService compatibilityStateService,
      MeetingJoinConfigurationReadinessService meetingJoinConfigurationReadinessService) {
    this.compatibilityStateService = compatibilityStateService;
    this.meetingJoinConfigurationReadinessService = meetingJoinConfigurationReadinessService;
  }

  @Override
  public HealthResponse getHealth() {
    return compatibilityStateService.findLatestIncompatibleActive()
        .map(this::createDownResponse)
        .orElseGet(() -> new HealthResponse("UP", "COMPATIBLE", null, null, null, null));
  }

  @Override
  public JoinReadinessResponse getJoinReadiness(String traceId) {
    HealthResponse health = getHealth();
    MeetingJoinConfigurationReadiness configurationReadiness = meetingJoinConfigurationReadinessService.inspect();

    List<JoinReadinessCheckResponse> checks = new ArrayList<>();
    checks.add(createBackendCheck(health));
    configurationReadiness.checks().stream()
        .map(this::toJoinReadinessCheck)
        .forEach(checks::add);

    return new JoinReadinessResponse(
        resolveJoinReadinessStatus(checks),
        Instant.now().toString(),
        traceId,
        configurationReadiness.publicJoinUrl(),
        List.copyOf(checks));
  }

  private HealthResponse createDownResponse(ConfigSetCompatibilityCheck check) {
    String diagnostics = check.mismatchCodes().stream()
        .map(code -> "MISMATCH: " + code)
        .collect(Collectors.joining(" | "));
    return new HealthResponse(
        "DOWN",
        "INCOMPATIBLE",
        check.configSetId(),
        diagnostics,
        check.traceId(),
        check.checkedAt().toString());
  }

  private JoinReadinessCheckResponse createBackendCheck(HealthResponse health) {
    if ("UP".equals(health.status())) {
      return new JoinReadinessCheckResponse(
          "backend",
          "ok",
          "Backend API доступен",
          "Конфигурация совместима, выдача токена доступа готова.",
          List.of("Можно продолжать вход во встречу"),
          null,
          false);
    }

    return new JoinReadinessCheckResponse(
        "backend",
        "error",
        "Backend блокирует вход во встречу",
        health.details() == null || health.details().isBlank()
            ? "Обнаружена несовместимая активная конфигурация."
            : health.details(),
        List.of("Проверить активный config set", "Исправить mismatch и повторить вход"),
        "CONFIG_INCOMPATIBLE",
        true);
  }

  private JoinReadinessCheckResponse toJoinReadinessCheck(
      MeetingJoinConfigurationReadiness.ConfigurationCheck check) {
    return new JoinReadinessCheckResponse(
        check.key(),
        check.status(),
        check.headline(),
        check.reason(),
        check.actions(),
        check.errorCode(),
        check.blocking());
  }

  private String resolveJoinReadinessStatus(List<JoinReadinessCheckResponse> checks) {
    boolean hasBlockingFailure = checks.stream()
        .anyMatch(check -> check.blocking() && "error".equals(check.status()));
    if (hasBlockingFailure) {
      return "blocked";
    }

    boolean hasWarning = checks.stream()
        .anyMatch(check -> "warn".equals(check.status()) || "error".equals(check.status()));
    return hasWarning ? "degraded" : "ready";
  }
}
