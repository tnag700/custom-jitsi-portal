package com.acme.jitsi.domains.health.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.acme.jitsi.domains.configsets.service.ConfigSetCompatibilityCheck;
import com.acme.jitsi.domains.configsets.service.ConfigSetCompatibilityStateService;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DefaultHealthServiceTest {

  @Mock
  private ConfigSetCompatibilityStateService compatibilityStateService;

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

    DefaultHealthService service = new DefaultHealthService(compatibilityStateService);

    var response = service.getHealth();

    assertThat(response.status()).isEqualTo("DOWN");
    assertThat(response.compatibilityStatus()).isEqualTo("INCOMPATIBLE");
    assertThat(response.configSetId()).isEqualTo("cs-1");
  }

  @Test
  void getHealthReturnsUpWhenNoIncompatibleActiveConfigExists() {
    when(compatibilityStateService.findLatestIncompatibleActive()).thenReturn(Optional.empty());

    DefaultHealthService service = new DefaultHealthService(compatibilityStateService);

    var response = service.getHealth();

    assertThat(response.status()).isEqualTo("UP");
    assertThat(response.compatibilityStatus()).isEqualTo("COMPATIBLE");
  }
}
