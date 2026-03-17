package com.acme.jitsi.shared.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.acme.jitsi.domains.configsets.service.ConfigSetCompatibilityCheck;
import com.acme.jitsi.domains.configsets.service.ConfigSetCompatibilityStateService;
import com.acme.jitsi.domains.health.dto.HealthResponse;
import com.acme.jitsi.domains.health.service.HealthService;
import com.acme.jitsi.domains.meetings.service.MeetingJoinConfigurationReadiness;
import com.acme.jitsi.domains.meetings.service.MeetingJoinConfigurationReadinessService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PhaseOneServiceStabilityMetricsTest {

  @Mock
  private ConfigSetCompatibilityStateService compatibilityStateService;

  @Mock
  private HealthService healthService;

  @Mock
  private MeetingJoinConfigurationReadinessService meetingJoinConfigurationReadinessService;

  @Test
  void exposesBackendConfigAndJoinReadinessGaugesFromExistingSignals() {
    when(compatibilityStateService.findLatestIncompatibleActive()).thenReturn(Optional.of(
        new ConfigSetCompatibilityCheck(
            "chk-1",
            "config-1",
            false,
            List.of("ISSUER_MISMATCH"),
            "issuer mismatch",
            Instant.parse("2026-03-16T12:15:00Z"),
            "trace-health-1")));
        when(healthService.getHealth()).thenReturn(
          new HealthResponse("DOWN", "INCOMPATIBLE", "config-1", "issuer mismatch", "trace-health-1", "2026-03-16T12:15:00Z"));
    when(meetingJoinConfigurationReadinessService.inspect()).thenReturn(
        new MeetingJoinConfigurationReadiness(
            "https://meet.example/join/demo",
            List.of(
                new MeetingJoinConfigurationReadiness.ConfigurationCheck(
                    "token-config",
                    "error",
                    "JWT config invalid",
                    "secret missing",
                    List.of("fix config"),
                    "TOKEN_CONFIG_INVALID",
                    true))));

    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

    try {
      PhaseOneServiceStabilityMetrics metrics = new PhaseOneServiceStabilityMetrics(
          meterRegistry,
          healthService,
          compatibilityStateService,
          meetingJoinConfigurationReadinessService);

      assertThat(meterRegistry.get("jitsi.service.backend.available").gauge().value()).isZero();
      assertThat(meterRegistry.get("jitsi.service.config.compatible").gauge().value()).isZero();
      assertThat(meterRegistry.get("jitsi.service.join_readiness.blocked").gauge().value()).isEqualTo(1.0d);
      assertThat(meterRegistry.get("jitsi.service.join_readiness.ready").gauge().value()).isZero();
      assertThat(meterRegistry.get("jitsi.service.join_readiness.degraded").gauge().value()).isZero();
      assertThat(metrics).isNotNull();
    } finally {
      meterRegistry.close();
    }
  }

  @Test
  void registersAvailabilityAndCompatibilityWithoutReadinessDependency() {
    when(healthService.getHealth()).thenReturn(
        new HealthResponse("UP", "COMPATIBLE", null, null, null, null));
    when(compatibilityStateService.findLatestIncompatibleActive()).thenReturn(Optional.empty());

    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

    try {
      PhaseOneServiceStabilityMetrics metrics = new PhaseOneServiceStabilityMetrics(
          meterRegistry,
          healthService,
          compatibilityStateService,
          null);

      assertThat(meterRegistry.get("jitsi.service.backend.available").gauge().value()).isEqualTo(1.0d);
      assertThat(meterRegistry.get("jitsi.service.config.compatible").gauge().value()).isEqualTo(1.0d);
      assertThat(meterRegistry.find("jitsi.service.join_readiness.ready").gauge()).isNull();
      assertThat(meterRegistry.find("jitsi.service.join_readiness.degraded").gauge()).isNull();
      assertThat(meterRegistry.find("jitsi.service.join_readiness.blocked").gauge()).isNull();
      assertThat(metrics).isNotNull();
    } finally {
      meterRegistry.close();
    }
  }

  @Test
  void backendAvailabilityUsesHealthFoundationRatherThanRawCompatibilityGauge() {
    when(healthService.getHealth()).thenReturn(
        new HealthResponse("UP", "COMPATIBLE", null, null, null, null));
    when(compatibilityStateService.findLatestIncompatibleActive()).thenReturn(Optional.of(
        new ConfigSetCompatibilityCheck(
            "chk-2",
            "config-2",
            false,
            List.of("ISSUER_MISMATCH"),
            "issuer mismatch",
            Instant.parse("2026-03-16T12:20:00Z"),
            "trace-health-2")));

    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

    try {
      PhaseOneServiceStabilityMetrics metrics = new PhaseOneServiceStabilityMetrics(
          meterRegistry,
          healthService,
          compatibilityStateService,
          mock(MeetingJoinConfigurationReadinessService.class));

      assertThat(meterRegistry.get("jitsi.service.backend.available").gauge().value()).isEqualTo(1.0d);
      assertThat(meterRegistry.get("jitsi.service.config.compatible").gauge().value()).isZero();
      assertThat(metrics).isNotNull();
    } finally {
      meterRegistry.close();
    }
  }

  @Test
  void exposesDegradedGaugeForNonBlockingJoinReadinessWarnings() {
    when(meetingJoinConfigurationReadinessService.inspect()).thenReturn(
        new MeetingJoinConfigurationReadiness(
            "http://meet.example.test/",
            List.of(
                new MeetingJoinConfigurationReadiness.ConfigurationCheck(
                    "join-url",
                    "warn",
                    "Join URL uses HTTP",
                    "TLS is not configured",
                    List.of("enable HTTPS"),
                    "JOIN_URL_NOT_HTTPS",
                    false))));

    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

    try {
      PhaseOneServiceStabilityMetrics metrics = new PhaseOneServiceStabilityMetrics(
          meterRegistry,
          healthService,
          compatibilityStateService,
          meetingJoinConfigurationReadinessService);

      assertThat(meterRegistry.get("jitsi.service.join_readiness.degraded").gauge().value()).isEqualTo(1.0d);
      assertThat(meterRegistry.get("jitsi.service.join_readiness.ready").gauge().value()).isZero();
      assertThat(meterRegistry.get("jitsi.service.join_readiness.blocked").gauge().value()).isZero();
      assertThat(metrics).isNotNull();
    } finally {
      meterRegistry.close();
    }
  }
}