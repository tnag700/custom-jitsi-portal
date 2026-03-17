package com.acme.jitsi.domains.health.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.acme.jitsi.domains.configsets.service.ConfigSetCompatibilityCheck;
import com.acme.jitsi.domains.configsets.service.ConfigSetCompatibilityStateService;
import com.acme.jitsi.domains.meetings.service.MeetingJoinConfigurationReadiness;
import com.acme.jitsi.domains.meetings.service.MeetingJoinConfigurationReadinessService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DefaultHealthServiceTest {

  @Mock
  private ConfigSetCompatibilityStateService compatibilityStateService;

  @Mock
  private MeetingJoinConfigurationReadinessService meetingJoinConfigurationReadinessService;

  @Test
  void getHealthReturnsDownWhenIncompatibleActiveConfigExists() {
    when(compatibilityStateService.findLatestIncompatibleActive()).thenReturn(Optional.of(
        new ConfigSetCompatibilityCheck(
            "chk-1",
            "cs-1",
            false,
            java.util.List.of("ISSUER_MISMATCH"),
            "issuer mismatch",
            Instant.parse("2026-01-01T00:00:00Z"),
            "trace-1")));

    DefaultHealthService service = new DefaultHealthService(
      compatibilityStateService,
      meetingJoinConfigurationReadinessService);

    var response = service.getHealth();

    assertThat(response.status()).isEqualTo("DOWN");
    assertThat(response.compatibilityStatus()).isEqualTo("INCOMPATIBLE");
    assertThat(response.configSetId()).isEqualTo("cs-1");
  }

  @Test
  void getHealthReturnsUpWhenNoIncompatibleActiveConfigExists() {
    when(compatibilityStateService.findLatestIncompatibleActive()).thenReturn(Optional.empty());

    DefaultHealthService service = new DefaultHealthService(
        compatibilityStateService,
        meetingJoinConfigurationReadinessService);

    var response = service.getHealth();

    assertThat(response.status()).isEqualTo("UP");
    assertThat(response.compatibilityStatus()).isEqualTo("COMPATIBLE");
  }

  @Test
  void getJoinReadinessReturnsBlockedWhenAnyBlockingCheckFails() {
    when(compatibilityStateService.findLatestIncompatibleActive()).thenReturn(Optional.empty());
    when(meetingJoinConfigurationReadinessService.inspect()).thenReturn(
        new MeetingJoinConfigurationReadiness(
            null,
            List.of(
                new MeetingJoinConfigurationReadiness.ConfigurationCheck(
                    "token-config",
                    "error",
                    "JWT-конфиг входа не готов",
                    "signingSecret missing",
                    List.of("fix config"),
                    "TOKEN_CONFIG_INVALID",
                    true))));

    DefaultHealthService service = new DefaultHealthService(
        compatibilityStateService,
        meetingJoinConfigurationReadinessService);

    var response = service.getJoinReadiness("trace-join");

    assertThat(response.status()).isEqualTo("blocked");
    assertThat(response.traceId()).isEqualTo("trace-join");
    assertThat(response.systemChecks()).hasSize(2);
  }

  @Test
  void getJoinReadinessReturnsDegradedWhenOnlyWarningChecksExist() {
    when(compatibilityStateService.findLatestIncompatibleActive()).thenReturn(Optional.empty());
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

    DefaultHealthService service = new DefaultHealthService(
        compatibilityStateService,
        meetingJoinConfigurationReadinessService);

    var response = service.getJoinReadiness("trace-warn");

    assertThat(response.status()).isEqualTo("degraded");
    assertThat(response.publicJoinUrl()).isEqualTo("http://meet.example.test/");
    assertThat(response.systemChecks()).hasSize(2);
  }
}
