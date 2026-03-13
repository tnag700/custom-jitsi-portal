package com.acme.jitsi.domains.configsets.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.acme.jitsi.shared.observability.FlowObservationFacade;
import com.acme.jitsi.shared.observability.RecordedObservationHandler;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ConfigCompatibilityTracingTest {

  private final RecordedObservationHandler observations = new RecordedObservationHandler();

  private ConfigSetCompatibilityCheckRepository compatibilityCheckRepository;
  private ConfigSetRepository configSetRepository;
  private ConfigSetCompatibilityStateService service;

  @BeforeEach
  void setUp() {
    compatibilityCheckRepository = Mockito.mock(ConfigSetCompatibilityCheckRepository.class);
    configSetRepository = Mockito.mock(ConfigSetRepository.class);
    service = new ConfigSetCompatibilityStateService(
        compatibilityCheckRepository,
        configSetRepository,
        new FlowObservationFacade(observations.createRegistry()));
    observations.reset();
  }

  @Test
  void recordEmitsCompatibilityRecordObservationForCompatibleResult() {
    ConfigSetCompatibilityCheck saved = new ConfigSetCompatibilityCheck(
        "chk-2", "cs-2", true, List.of(), "", Instant.parse("2026-01-01T00:00:00Z"), "trace-2");
    when(compatibilityCheckRepository.save(Mockito.any())).thenReturn(saved);

    ConfigCompatibilityCheckResult result = new ConfigCompatibilityCheckResult(
        true, List.of(), Instant.parse("2026-01-01T00:00:00Z"), "trace-2");
    service.record("cs-2", result);

    RecordedObservationHandler.RecordedObservation observation = observations.only("config.compatibility.check");
    assertThat(observation.lowCardinality())
        .containsEntry("flow.stage", "compatibility_record")
        .containsEntry("flow.outcome", "success")
        .containsEntry("flow.compatibility", "compatible");
    assertThat(observation.lowCardinality().toString())
        .doesNotContain("cs-2")
        .doesNotContain("trace-2");
  }

  @Test
  void recordEmitsCompatibilityRecordObservationForIncompatibleResult() {
    ConfigSetCompatibilityCheck saved = new ConfigSetCompatibilityCheck(
        "chk-3", "cs-3", false, List.of("ISSUER_MISMATCH"), "issuer mismatch", Instant.parse("2026-01-01T00:00:00Z"), "trace-3");
    when(compatibilityCheckRepository.save(Mockito.any())).thenReturn(saved);

    ConfigCompatibilityCheckResult result = new ConfigCompatibilityCheckResult(
        false,
        List.of(new ConfigCompatibilityMismatch(ConfigCompatibilityMismatchCode.ISSUER_MISMATCH, "issuer mismatch", "a", "b")),
        Instant.parse("2026-01-01T00:00:00Z"),
        "trace-3");
    service.record("cs-3", result);

    RecordedObservationHandler.RecordedObservation observation = observations.only("config.compatibility.check");
    assertThat(observation.lowCardinality())
        .containsEntry("flow.stage", "compatibility_record")
        .containsEntry("flow.outcome", "compatibility_failure")
        .containsEntry("flow.compatibility", "incompatible");
    assertThat(observation.lowCardinality().toString())
        .doesNotContain("cs-3")
        .doesNotContain("issuer mismatch");
  }

  @Test
  void findLatestIncompatibleActiveEmitsLookupObservationWithoutMismatchLeakage() {
    when(configSetRepository.findByStatus(ConfigSetStatus.ACTIVE)).thenReturn(List.of(configSet("cs-1")));
    when(compatibilityCheckRepository.findLatestByConfigSetIds(List.of("cs-1"))).thenReturn(List.of(
        new ConfigSetCompatibilityCheck(
            "chk-1",
            "cs-1",
            false,
            List.of("ISSUER_MISMATCH"),
            "issuer mismatch; audience mismatch",
            Instant.parse("2026-01-01T00:00:00Z"),
            "trace-1")));

    service.findLatestIncompatibleActive();

    RecordedObservationHandler.RecordedObservation observation = observations.only("config.compatibility.check");
    assertThat(observation.lowCardinality())
        .containsEntry("flow.stage", "compatibility_lookup")
        .containsEntry("flow.outcome", "compatibility_failure")
        .containsEntry("flow.compatibility", "incompatible");
    assertThat(observation.lowCardinality().toString())
        .doesNotContain("cs-1")
        .doesNotContain("issuer mismatch");
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