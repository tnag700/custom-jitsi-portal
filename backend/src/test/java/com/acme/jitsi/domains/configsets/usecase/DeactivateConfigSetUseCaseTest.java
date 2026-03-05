package com.acme.jitsi.domains.configsets.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.acme.jitsi.domains.configsets.event.ConfigSetDeactivatedEvent;
import com.acme.jitsi.domains.configsets.service.ConfigSet;
import com.acme.jitsi.domains.configsets.service.ConfigSetEnvironmentType;
import com.acme.jitsi.domains.configsets.service.ConfigSetNotFoundException;
import com.acme.jitsi.domains.configsets.service.ConfigSetRepository;
import com.acme.jitsi.domains.configsets.service.ConfigSetStatus;
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
class DeactivateConfigSetUseCaseTest {

  @Mock
  private ConfigSetRepository repository;
  @Mock
  private ApplicationEventPublisher eventPublisher;

  private DeactivateConfigSetUseCase useCase;

  @BeforeEach
  void setUp() {
    useCase = new DeactivateConfigSetUseCase(
        repository,
        eventPublisher,
        Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
  }

  @Test
  void executeSetsStatusToInactive() {
    ConfigSet existing = new ConfigSet(
        "cs-1",
        "Config",
        "tenant-1",
        ConfigSetEnvironmentType.DEV,
        "issuer",
        "audience",
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
    when(repository.findById("cs-1")).thenReturn(Optional.of(existing));
    when(repository.save(any(ConfigSet.class))).thenAnswer(invocation -> invocation.getArgument(0));

    ConfigSet updated = useCase.execute(new DeactivateConfigSetCommand("cs-1", "tenant-1", "actor-1", "trace-1"));

    assertThat(updated.status()).isEqualTo(ConfigSetStatus.INACTIVE);
    verify(eventPublisher).publishEvent(any(ConfigSetDeactivatedEvent.class));
  }

  @Test
  void executeRejectsTenantMismatch() {
    ConfigSet existing = new ConfigSet(
        "cs-1",
        "Config",
        "tenant-1",
        ConfigSetEnvironmentType.DEV,
        "issuer",
        "audience",
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
    when(repository.findById("cs-1")).thenReturn(Optional.of(existing));

    assertThatThrownBy(() -> useCase.execute(
        new DeactivateConfigSetCommand("cs-1", "tenant-2", "actor-1", "trace-1")))
        .isInstanceOf(ConfigSetNotFoundException.class);
  }
}