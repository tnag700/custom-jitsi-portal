package com.acme.jitsi.domains.configsets.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.acme.jitsi.domains.configsets.event.ConfigSetRolloutCompletedEvent;
import com.acme.jitsi.domains.configsets.service.ConfigCompatibilityCheckResult;
import com.acme.jitsi.domains.configsets.service.ConfigSet;
import com.acme.jitsi.domains.configsets.service.ConfigSetCompatibilityStateService;
import com.acme.jitsi.domains.configsets.service.ConfigSetDryRunValidator;
import com.acme.jitsi.domains.configsets.service.ConfigSetEnvironmentType;
import com.acme.jitsi.domains.configsets.service.ConfigSetNotFoundException;
import com.acme.jitsi.domains.configsets.service.ConfigSetRepository;
import com.acme.jitsi.domains.configsets.service.ConfigSetRollout;
import com.acme.jitsi.domains.configsets.service.ConfigSetRolloutNotAllowedException;
import com.acme.jitsi.domains.configsets.service.ConfigSetRolloutRepository;
import com.acme.jitsi.domains.configsets.service.ConfigSetRolloutValidationFailedException;
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
class RolloutConfigSetUseCaseTest {

  @Mock
  private ConfigSetRepository configSetRepository;
  @Mock
  private ConfigSetRolloutRepository rolloutRepository;
  @Mock
  private ConfigSetDryRunValidator dryRunValidator;
  @Mock
  private ConfigSetCompatibilityStateService compatibilityStateService;
  @Mock
  private ActivateConfigSetUseCase activateConfigSetUseCase;
  @Mock
  private ApplicationEventPublisher eventPublisher;

  private RolloutConfigSetUseCase useCase;

  @BeforeEach
  void setUp() {
    useCase = new RolloutConfigSetUseCase(
        configSetRepository,
        rolloutRepository,
        dryRunValidator,
        compatibilityStateService,
        activateConfigSetUseCase,
        eventPublisher,
        Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
    lenient().when(rolloutRepository.save(any(ConfigSetRollout.class))).thenAnswer(invocation -> invocation.getArgument(0));
  }

  @Test
  void executeSucceedsAndPublishesEvent() {
    ConfigSet target = draft("cs-1");
    when(configSetRepository.findById("cs-1")).thenReturn(Optional.of(target));
    when(configSetRepository.findActiveByTenantIdAndEnvironmentType("tenant-1", ConfigSetEnvironmentType.DEV))
        .thenReturn(Optional.empty());
    when(dryRunValidator.validateCompatibility(any(), any())).thenReturn(new ConfigCompatibilityCheckResult(
      true,
      java.util.List.of(),
      Instant.parse("2026-01-01T00:00:00Z"),
      "trace-1"));

    ConfigSetRollout rollout = useCase.execute(new RolloutConfigSetCommand("cs-1", "tenant-1", "actor-1", "trace-1"));

    assertThat(rollout.status()).isEqualTo(RolloutStatus.SUCCEEDED);
    verify(eventPublisher).publishEvent(any(ConfigSetRolloutCompletedEvent.class));
  }

  @Test
  void executeFailsWhenDryRunValidationFails() {
    ConfigSet target = draft("cs-1");
    when(configSetRepository.findById("cs-1")).thenReturn(Optional.of(target));
    when(dryRunValidator.validateCompatibility(any(), any())).thenReturn(new ConfigCompatibilityCheckResult(
      false,
      java.util.List.of(),
      Instant.parse("2026-01-01T00:00:00Z"),
      "trace-1"));

    assertThatThrownBy(() -> useCase.execute(new RolloutConfigSetCommand("cs-1", "tenant-1", "actor-1", "trace-1")))
        .isInstanceOf(ConfigSetRolloutValidationFailedException.class);
  }

  @Test
  void executeThrowsNotFoundWhenConfigSetMissing() {
    when(configSetRepository.findById("missing")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> useCase.execute(new RolloutConfigSetCommand("missing", "tenant-1", "actor-1", "trace-1")))
        .isInstanceOf(ConfigSetNotFoundException.class);
  }

  @Test
  void executeThrowsNotFoundWhenTenantIdNull() {
    when(configSetRepository.findById("cs-1")).thenReturn(Optional.of(draft("cs-1")));

    assertThatThrownBy(() -> useCase.execute(new RolloutConfigSetCommand("cs-1", null, "actor-1", "trace-1")))
        .isInstanceOf(ConfigSetNotFoundException.class);
  }

  @Test
  void executeThrowsNotFoundWhenTenantIdBlank() {
    when(configSetRepository.findById("cs-1")).thenReturn(Optional.of(draft("cs-1")));

    assertThatThrownBy(() -> useCase.execute(new RolloutConfigSetCommand("cs-1", "  ", "actor-1", "trace-1")))
        .isInstanceOf(ConfigSetNotFoundException.class);
  }

  @Test
  void executeThrowsNotFoundWhenTenantIdMismatch() {
    when(configSetRepository.findById("cs-1")).thenReturn(Optional.of(draft("cs-1")));

    assertThatThrownBy(() -> useCase.execute(new RolloutConfigSetCommand("cs-1", "other-tenant", "actor-1", "trace-1")))
        .isInstanceOf(ConfigSetNotFoundException.class);
  }

  @Test
  void executeThrowsRolloutNotAllowedWhenNotDraft() {
    ConfigSet active = new ConfigSet(
        "cs-1", "Config", "tenant-1", ConfigSetEnvironmentType.DEV,
        "https://portal.example.test", "jitsi-meet", "HS256", "role",
        "secret", null, 20, 120, "https://meet.example.test",
        ConfigSetStatus.ACTIVE,
        Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-01T00:00:00Z"));
    when(configSetRepository.findById("cs-1")).thenReturn(Optional.of(active));

    assertThatThrownBy(() -> useCase.execute(new RolloutConfigSetCommand("cs-1", "tenant-1", "actor-1", "trace-1")))
        .isInstanceOf(ConfigSetRolloutNotAllowedException.class);
  }

  @Test
  void executeRecordsPreviousConfigSetIdWhenActiveExists() {
    ConfigSet target = draft("cs-new");
    ConfigSet active = new ConfigSet(
        "cs-old", "Old Config", "tenant-1", ConfigSetEnvironmentType.DEV,
        "https://portal.example.test", "jitsi-meet", "HS256", "role",
        "secret", null, 20, 120, "https://meet.example.test",
        ConfigSetStatus.ACTIVE,
        Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-01T00:00:00Z"));
    when(configSetRepository.findById("cs-new")).thenReturn(Optional.of(target));
    when(configSetRepository.findActiveByTenantIdAndEnvironmentType("tenant-1", ConfigSetEnvironmentType.DEV))
        .thenReturn(Optional.of(active));
    when(dryRunValidator.validateCompatibility(any(), any())).thenReturn(new ConfigCompatibilityCheckResult(
      true,
      java.util.List.of(),
      Instant.parse("2026-01-01T00:00:00Z"),
      "trace-1"));

    ConfigSetRollout rollout = useCase.execute(new RolloutConfigSetCommand("cs-new", "tenant-1", "actor-1", "trace-1"));

    assertThat(rollout.previousConfigSetId()).isEqualTo("cs-old");
    assertThat(rollout.status()).isEqualTo(RolloutStatus.SUCCEEDED);
  }

  private ConfigSet draft(String configSetId) {
    return new ConfigSet(
        configSetId,
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
        ConfigSetStatus.DRAFT,
        Instant.parse("2026-01-01T00:00:00Z"),
        Instant.parse("2026-01-01T00:00:00Z"));
  }
}