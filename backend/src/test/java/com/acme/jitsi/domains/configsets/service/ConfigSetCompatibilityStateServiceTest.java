package com.acme.jitsi.domains.configsets.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConfigSetCompatibilityStateServiceTest {

  @Mock
  private ConfigSetCompatibilityCheckRepository compatibilityCheckRepository;

  @Mock
  private ConfigSetRepository configSetRepository;

  @Test
  void findLatestIncompatibleActiveUsesBulkLookupAndReturnsLatestIncompatible() {
    ConfigSet first = configSet("cs-1");
    ConfigSet second = configSet("cs-2");

    when(configSetRepository.findByStatus(ConfigSetStatus.ACTIVE)).thenReturn(List.of(first, second));
    when(compatibilityCheckRepository.findLatestByConfigSetIds(List.of("cs-1", "cs-2"))).thenReturn(List.of(
        new ConfigSetCompatibilityCheck(
            "chk-1",
            "cs-1",
            false,
            List.of("ISSUER_MISMATCH"),
            "issuer mismatch",
            Instant.parse("2026-01-01T00:00:00Z"),
            "trace-1"),
        new ConfigSetCompatibilityCheck(
            "chk-2",
            "cs-2",
            false,
            List.of("API_VERSION_MISMATCH"),
            "version mismatch",
            Instant.parse("2026-01-01T00:01:00Z"),
            "trace-2")));

    ConfigSetCompatibilityStateService service = new ConfigSetCompatibilityStateService(
        compatibilityCheckRepository,
        configSetRepository);

    Optional<ConfigSetCompatibilityCheck> result = service.findLatestIncompatibleActive();

    assertThat(result).isPresent();
    assertThat(result.orElseThrow().configSetId()).isEqualTo("cs-2");
    verify(compatibilityCheckRepository).findLatestByConfigSetIds(List.of("cs-1", "cs-2"));
  }

  private ConfigSet configSet(String id) {
    return new ConfigSet(
        id,
        "Config",
        "tenant-1",
        ConfigSetEnvironmentType.DEV,
        "https://issuer",
        "audience",
        "HS256",
        "role",
        "secret",
        null,
        20,
        120,
        "https://meet.example.test/v1",
        ConfigSetStatus.ACTIVE,
        Instant.parse("2026-01-01T00:00:00Z"),
        Instant.parse("2026-01-01T00:00:00Z"));
  }
}
