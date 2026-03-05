package com.acme.jitsi.domains.configsets.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.acme.jitsi.domains.configsets.event.ConfigSetActivatedEvent;
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

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class ActivateConfigSetUseCaseTest {

  @Mock
  private ConfigSetRepository repository;
  @Mock
  private ApplicationEventPublisher eventPublisher;

  private ActivateConfigSetUseCase useCase;

  @BeforeEach
  void setUp() {
    useCase = new ActivateConfigSetUseCase(
        repository,
        eventPublisher,
        Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
  }

  @Test
  void executeDeactivatesPreviousAndActivatesTarget() {
    ConfigSet currentActive = base("cs-old", ConfigSetStatus.ACTIVE);
    ConfigSet target = base("cs-new", ConfigSetStatus.DRAFT);
    when(repository.findById("cs-new")).thenReturn(Optional.of(target));
    when(repository.findActiveByTenantIdAndEnvironmentType("tenant-1", ConfigSetEnvironmentType.DEV))
        .thenReturn(Optional.of(currentActive));
    when(repository.save(any(ConfigSet.class))).thenAnswer(invocation -> invocation.getArgument(0));

    ConfigSet activated = useCase.execute(new ActivateConfigSetCommand("cs-new", "tenant-1", "actor-1", "trace-1"));

    assertThat(activated.status()).isEqualTo(ConfigSetStatus.ACTIVE);
    verify(repository, atLeastOnce()).save(any(ConfigSet.class));
    verify(eventPublisher).publishEvent(any(ConfigSetDeactivatedEvent.class));
    verify(eventPublisher).publishEvent(any(ConfigSetActivatedEvent.class));
  }

  @Test
  void executeRejectsTenantMismatch() {
    ConfigSet target = base("cs-new", ConfigSetStatus.DRAFT);
    when(repository.findById("cs-new")).thenReturn(Optional.of(target));

    assertThatThrownBy(() -> useCase.execute(
        new ActivateConfigSetCommand("cs-new", "tenant-2", "actor-1", "trace-1")))
        .isInstanceOf(ConfigSetNotFoundException.class);
  }

  private ConfigSet base(String id, ConfigSetStatus status) {
    return new ConfigSet(
        id,
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
        status,
        Instant.parse("2026-01-01T00:00:00Z"),
        Instant.parse("2026-01-01T00:00:00Z"));
  }
}