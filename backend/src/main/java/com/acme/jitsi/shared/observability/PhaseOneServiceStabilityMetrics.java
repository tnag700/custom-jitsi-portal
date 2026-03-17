package com.acme.jitsi.shared.observability;

import com.acme.jitsi.domains.configsets.service.ConfigSetCompatibilityStateService;
import com.acme.jitsi.domains.health.service.HealthService;
import com.acme.jitsi.domains.meetings.service.MeetingJoinConfigurationReadiness;
import com.acme.jitsi.domains.meetings.service.MeetingJoinConfigurationReadinessService;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
public class PhaseOneServiceStabilityMetrics {

  private final HealthService healthService;
  private final ConfigSetCompatibilityStateService compatibilityStateService;
  private final MeetingJoinConfigurationReadinessService meetingJoinConfigurationReadinessService;

  @Autowired
  public PhaseOneServiceStabilityMetrics(
      MeterRegistry meterRegistry,
      ObjectProvider<HealthService> healthServiceProvider,
      ObjectProvider<ConfigSetCompatibilityStateService> compatibilityStateServiceProvider,
      ObjectProvider<MeetingJoinConfigurationReadinessService> meetingJoinConfigurationReadinessServiceProvider) {
    this(
        meterRegistry,
        healthServiceProvider.getIfAvailable(),
        compatibilityStateServiceProvider.getIfAvailable(),
        meetingJoinConfigurationReadinessServiceProvider.getIfAvailable());
  }

  PhaseOneServiceStabilityMetrics(
      MeterRegistry meterRegistry,
      HealthService healthService,
      ConfigSetCompatibilityStateService compatibilityStateService,
      MeetingJoinConfigurationReadinessService meetingJoinConfigurationReadinessService) {
    this.healthService = healthService;
    this.compatibilityStateService = compatibilityStateService;
    this.meetingJoinConfigurationReadinessService = meetingJoinConfigurationReadinessService;

    if (healthService != null) {
      Gauge.builder("jitsi.service.backend.available", this, PhaseOneServiceStabilityMetrics::backendAvailable)
          .description("1 when backend health surface reports UP; otherwise 0")
          .register(meterRegistry);
    }

    if (compatibilityStateService != null) {
      Gauge.builder("jitsi.service.config.compatible", this, PhaseOneServiceStabilityMetrics::configCompatible)
          .description("1 when active config set is compatible; otherwise 0")
          .register(meterRegistry);
    }

    if (meetingJoinConfigurationReadinessService != null) {
      Gauge.builder("jitsi.service.join_readiness.ready", this, metrics -> metrics.joinReadinessState("ready"))
          .description("1 when join readiness state is ready; otherwise 0")
          .register(meterRegistry);
      Gauge.builder("jitsi.service.join_readiness.degraded", this, metrics -> metrics.joinReadinessState("degraded"))
          .description("1 when join readiness state is degraded; otherwise 0")
          .register(meterRegistry);
      Gauge.builder("jitsi.service.join_readiness.blocked", this, metrics -> metrics.joinReadinessState("blocked"))
          .description("1 when join readiness state is blocked; otherwise 0")
          .register(meterRegistry);
    }
  }

  double backendAvailable() {
    if (healthService == null) {
      return 0.0d;
    }
    return "UP".equals(healthService.getHealth().status()) ? 1.0d : 0.0d;
  }

  double configCompatible() {
    if (compatibilityStateService == null) {
      return 0.0d;
    }
    return compatibilityStateService.findLatestIncompatibleActive().isPresent() ? 0.0d : 1.0d;
  }

  double joinReadinessState(String expectedState) {
    if (meetingJoinConfigurationReadinessService == null) {
      return 0.0d;
    }
    return expectedState.equals(resolveJoinReadinessState()) ? 1.0d : 0.0d;
  }

  private String resolveJoinReadinessState() {
    MeetingJoinConfigurationReadiness readiness = meetingJoinConfigurationReadinessService.inspect();
    List<MeetingJoinConfigurationReadiness.ConfigurationCheck> checks = readiness.checks();

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