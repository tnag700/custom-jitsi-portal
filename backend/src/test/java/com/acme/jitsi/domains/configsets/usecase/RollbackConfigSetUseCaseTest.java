package com.acme.jitsi.domains.configsets.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.acme.jitsi.domains.configsets.event.ConfigSetRollbackCompletedEvent;
import com.acme.jitsi.domains.configsets.service.ConfigCompatibilityCheckResult;
import com.acme.jitsi.domains.configsets.service.ConfigSet;
import com.acme.jitsi.domains.configsets.service.ConfigSetCompatibilityStateService;
import com.acme.jitsi.domains.configsets.service.ConfigSetDryRunValidator;
import com.acme.jitsi.domains.configsets.service.ConfigSetEnvironmentType;
import com.acme.jitsi.domains.configsets.service.ConfigSetNotFoundException;
import com.acme.jitsi.domains.configsets.service.ConfigSetRepository;
import com.acme.jitsi.domains.configsets.service.ConfigSetRollbackNotAllowedException;
import com.acme.jitsi.domains.configsets.service.ConfigSetRollout;
import com.acme.jitsi.domains.configsets.service.ConfigSetRolloutRepository;
import com.acme.jitsi.domains.configsets.service.ConfigSetStatus;
import com.acme.jitsi.domains.configsets.service.RolloutStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class RollbackConfigSetUseCaseTest {

  @Mock
  private ConfigSetRepository configSetRepository;
  @Mock
  private ConfigSetRolloutRepository rolloutRepository;
  @Mock
  private ActivateConfigSetUseCase activateConfigSetUseCase;
  @Mock
  private ConfigSetDryRunValidator configSetDryRunValidator;
  @Mock
  private ConfigSetCompatibilityStateService compatibilityStateService;
  @Mock
  private ApplicationEventPublisher eventPublisher;
  @Mock
  private Clock clock;

  private RollbackConfigSetUseCase useCase;

  @BeforeEach
  void setUp() {
    useCase = new RollbackConfigSetUseCase(
        configSetRepository,
        rolloutRepository,
        activateConfigSetUseCase,
        configSetDryRunValidator,
        compatibilityStateService,
        eventPublisher,
        clock);
        org.mockito.Mockito.lenient().when(clock.instant()).thenReturn(Instant.parse("2026-01-01T00:00:00Z"));
    // Mock compatibility state service to avoid NPE when recording checks
        org.mockito.Mockito.lenient().when(compatibilityStateService.record(any(), any())).thenAnswer(invocation -> {
      var configSetId = (String) invocation.getArgument(0);
      var result = (ConfigCompatibilityCheckResult) invocation.getArgument(1);
      return new com.acme.jitsi.domains.configsets.service.ConfigSetCompatibilityCheck(
          java.util.UUID.randomUUID().toString(), configSetId, result.compatible(), 
          java.util.List.of(), "", result.checkedAt(), result.traceId());
    });
  }

  @Test
  void executeRollsBackToPreviousConfigSetAndPublishesEvent() {
    ConfigSet current = config("cs-current");
    ConfigSet previous = config("cs-prev");
    ConfigSetRollout latest = new ConfigSetRollout(
        "r-1",
        "cs-current",
        "cs-prev",
        "tenant-1",
        ConfigSetEnvironmentType.DEV,
        RolloutStatus.SUCCEEDED,
        null,
        Instant.parse("2026-01-01T00:00:00Z"),
        Instant.parse("2026-01-01T00:01:00Z"),
        "actor-1",
        "trace-1");

    when(configSetRepository.findById("cs-current")).thenReturn(Optional.of(current));
    when(rolloutRepository.save(any(ConfigSetRollout.class))).thenAnswer(invocation -> invocation.getArgument(0));
    when(rolloutRepository.findLatestSucceededByTenantIdAndEnvironmentType("tenant-1", ConfigSetEnvironmentType.DEV))
        .thenReturn(Optional.of(latest));
    when(configSetRepository.findById("cs-prev")).thenReturn(Optional.of(previous));
    when(configSetRepository.findActiveByTenantIdAndEnvironmentType("tenant-1", ConfigSetEnvironmentType.DEV))
        .thenReturn(Optional.of(current));
    // Override default mock for this specific test
    when(configSetDryRunValidator.validateCompatibility(previous, "trace-1"))
        .thenReturn(new ConfigCompatibilityCheckResult(true, java.util.List.of(), Instant.now(), "trace-1"));

    ConfigSetRollout result = useCase.execute(new RollbackConfigSetCommand(
        "cs-current",
        "tenant-1",
        ConfigSetEnvironmentType.DEV,
        "actor-1",
        "trace-1"));

    assertThat(result.status()).isEqualTo(RolloutStatus.ROLLED_BACK);
    verify(eventPublisher).publishEvent(any(ConfigSetRollbackCompletedEvent.class));
  }

  @Test
  void executeFailsWhenPreviousConfigSetMissing() {
    ConfigSet current = config("cs-current");
    ConfigSetRollout latest = new ConfigSetRollout(
        "r-1",
        "cs-current",
        null,
        "tenant-1",
        ConfigSetEnvironmentType.DEV,
        RolloutStatus.SUCCEEDED,
        null,
        Instant.parse("2026-01-01T00:00:00Z"),
        Instant.parse("2026-01-01T00:01:00Z"),
        "actor-1",
        "trace-1");

    when(configSetRepository.findById("cs-current")).thenReturn(Optional.of(current));
    when(rolloutRepository.findLatestSucceededByTenantIdAndEnvironmentType("tenant-1", ConfigSetEnvironmentType.DEV))
        .thenReturn(Optional.of(latest));
    // Default compatible mock for tests where validateCompatibility is reached
    org.mockito.Mockito.lenient()
        .when(configSetDryRunValidator.validateCompatibility(any(), any()))
        .thenReturn(new ConfigCompatibilityCheckResult(true, java.util.List.of(), Instant.now(), "trace-1"));

    assertThatThrownBy(() -> useCase.execute(new RollbackConfigSetCommand(
        "cs-current",
        "tenant-1",
        ConfigSetEnvironmentType.DEV,
        "actor-1",
        "trace-1"))).isInstanceOf(ConfigSetRollbackNotAllowedException.class);
  }

  @Test
  void executeThrowsNotFoundWhenConfigSetMissing() {
    when(configSetRepository.findById("missing")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> useCase.execute(new RollbackConfigSetCommand(
        "missing", "tenant-1", ConfigSetEnvironmentType.DEV, "actor-1", "trace-1")))
        .isInstanceOf(ConfigSetNotFoundException.class);
  }

  @Test
  void executeThrowsNotFoundWhenTenantIdNull() {
    when(configSetRepository.findById("cs-current")).thenReturn(Optional.of(config("cs-current")));

    assertThatThrownBy(() -> useCase.execute(new RollbackConfigSetCommand(
        "cs-current", null, ConfigSetEnvironmentType.DEV, "actor-1", "trace-1")))
        .isInstanceOf(ConfigSetNotFoundException.class);
  }

  @Test
  void executeThrowsNotFoundWhenTenantIdBlank() {
    when(configSetRepository.findById("cs-current")).thenReturn(Optional.of(config("cs-current")));

    assertThatThrownBy(() -> useCase.execute(new RollbackConfigSetCommand(
        "cs-current", "  ", ConfigSetEnvironmentType.DEV, "actor-1", "trace-1")))
        .isInstanceOf(ConfigSetNotFoundException.class);
  }

  @Test
  void executeThrowsNotFoundWhenTenantIdMismatch() {
    when(configSetRepository.findById("cs-current")).thenReturn(Optional.of(config("cs-current")));

    assertThatThrownBy(() -> useCase.execute(new RollbackConfigSetCommand(
        "cs-current", "other-tenant", ConfigSetEnvironmentType.DEV, "actor-1", "trace-1")))
        .isInstanceOf(ConfigSetNotFoundException.class);
  }

  @Test
  void executeThrowsNotFoundWhenNoSucceededRolloutExists() {
    when(configSetRepository.findById("cs-current")).thenReturn(Optional.of(config("cs-current")));
    when(rolloutRepository.findLatestSucceededByTenantIdAndEnvironmentType("tenant-1", ConfigSetEnvironmentType.DEV))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> useCase.execute(new RollbackConfigSetCommand(
        "cs-current", "tenant-1", ConfigSetEnvironmentType.DEV, "actor-1", "trace-1")))
        .isInstanceOf(ConfigSetNotFoundException.class);
  }

  @Test
  void executeThrowsNotFoundWhenRolloutConfigSetIdDoesNotMatchCommand() {
    ConfigSetRollout latest = new ConfigSetRollout(
        "r-1", "cs-other", "cs-prev", "tenant-1", ConfigSetEnvironmentType.DEV,
        RolloutStatus.SUCCEEDED, null,
        Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-01T00:01:00Z"),
        "actor-1", "trace-1");

    when(configSetRepository.findById("cs-current")).thenReturn(Optional.of(config("cs-current")));
    when(rolloutRepository.findLatestSucceededByTenantIdAndEnvironmentType("tenant-1", ConfigSetEnvironmentType.DEV))
        .thenReturn(Optional.of(latest));

    assertThatThrownBy(() -> useCase.execute(new RollbackConfigSetCommand(
        "cs-current", "tenant-1", ConfigSetEnvironmentType.DEV, "actor-1", "trace-1")))
        .isInstanceOf(ConfigSetNotFoundException.class);
  }

  @Test
  void executeThrowsRollbackNotAllowedWhenPreviousConfigSetDeleted() {
    ConfigSetRollout latest = new ConfigSetRollout(
        "r-1", "cs-current", "cs-deleted", "tenant-1", ConfigSetEnvironmentType.DEV,
        RolloutStatus.SUCCEEDED, null,
        Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-01T00:01:00Z"),
        "actor-1", "trace-1");

    when(configSetRepository.findById("cs-current")).thenReturn(Optional.of(config("cs-current")));
    when(rolloutRepository.findLatestSucceededByTenantIdAndEnvironmentType("tenant-1", ConfigSetEnvironmentType.DEV))
        .thenReturn(Optional.of(latest));
    when(configSetRepository.findById("cs-deleted")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> useCase.execute(new RollbackConfigSetCommand(
        "cs-current", "tenant-1", ConfigSetEnvironmentType.DEV, "actor-1", "trace-1")))
        .isInstanceOf(ConfigSetRollbackNotAllowedException.class);
  }

  @Test
  void executeThrowsRollbackNotAllowedWhenPreviousConfigSetIncompatible() {
    ConfigSet current = config("cs-current");
    ConfigSet previous = config("cs-prev");
    ConfigSetRollout latest = new ConfigSetRollout(
        "r-1",
        "cs-current",
        "cs-prev",
        "tenant-1",
        ConfigSetEnvironmentType.DEV,
        RolloutStatus.SUCCEEDED,
        null,
        Instant.parse("2026-01-01T00:00:00Z"),
        Instant.parse("2026-01-01T00:01:00Z"),
        "actor-1",
        "trace-1");

    when(configSetRepository.findById("cs-current")).thenReturn(Optional.of(current));
    when(rolloutRepository.findLatestSucceededByTenantIdAndEnvironmentType("tenant-1", ConfigSetEnvironmentType.DEV))
        .thenReturn(Optional.of(latest));
    when(configSetRepository.findById("cs-prev")).thenReturn(Optional.of(previous));
    // Mock compatibility check - previous config is NOT compatible
    var mismatchList = java.util.List.of(
        new com.acme.jitsi.domains.configsets.service.ConfigCompatibilityMismatch(
            com.acme.jitsi.domains.configsets.service.ConfigCompatibilityMismatchCode.ISSUER_MISMATCH,
            "issuer mismatch", "expected", "actual"));
    when(configSetDryRunValidator.validateCompatibility(previous, "trace-1"))
        .thenReturn(new ConfigCompatibilityCheckResult(false, mismatchList, Instant.now(), "trace-1"));

    assertThatThrownBy(() -> useCase.execute(new RollbackConfigSetCommand(
        "cs-current", "tenant-1", ConfigSetEnvironmentType.DEV, "actor-1", "trace-1")))
        .isInstanceOf(ConfigSetRollbackNotAllowedException.class)
        .hasMessageContaining("incompatible");
  }

  private ConfigSet config(String id) {
    return new ConfigSet(
        id,
        "Config",
        "tenant-1",
        ConfigSetEnvironmentType.DEV,
        "https://portal.example.test",
        "jitsi-meet",
        "HS256",
        "role",
        "secret",
        null,
        20,
        120,
        "https://meet.example.test",
        ConfigSetStatus.ACTIVE,
        Instant.parse("2026-01-01T00:00:00Z"),
        Instant.parse("2026-01-01T00:00:00Z"));
  }
}