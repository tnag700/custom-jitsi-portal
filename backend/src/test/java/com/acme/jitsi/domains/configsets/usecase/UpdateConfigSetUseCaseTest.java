package com.acme.jitsi.domains.configsets.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.acme.jitsi.domains.configsets.event.ConfigSetUpdatedEvent;
import com.acme.jitsi.domains.configsets.service.ConfigSet;
import com.acme.jitsi.domains.configsets.service.ConfigSetEnvironmentType;
import com.acme.jitsi.domains.configsets.service.ConfigSetNotFoundException;
import com.acme.jitsi.domains.configsets.service.ConfigSetRepository;
import com.acme.jitsi.domains.configsets.service.ConfigSetStatus;
import com.acme.jitsi.security.JwtAlgorithmPolicy;
import com.acme.jitsi.security.JwtKeySource;
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
class UpdateConfigSetUseCaseTest {

  @Mock
  private ConfigSetRepository repository;
  @Mock
  private JwtAlgorithmPolicy jwtAlgorithmPolicy;
  @Mock
  private ApplicationEventPublisher eventPublisher;

  private UpdateConfigSetUseCase useCase;

  @BeforeEach
  void setUp() {
    useCase = new UpdateConfigSetUseCase(
        repository,
        jwtAlgorithmPolicy,
        eventPublisher,
        Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
  }

  @Test
  void executeUpdatesConfigSetAndPublishesEvent() {
    ConfigSet existing = new ConfigSet(
        "cs-1",
        "Config A",
        "tenant-1",
        ConfigSetEnvironmentType.DEV,
        "issuer-a",
        "audience-a",
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

    when(repository.findById("cs-1")).thenReturn(Optional.of(existing));
    when(repository.existsByNameAndTenantIdAndConfigSetIdNot("Config B", "tenant-1", "cs-1")).thenReturn(false);
    when(jwtAlgorithmPolicy.isSupportedForKeySource("HS256", JwtKeySource.SECRET)).thenReturn(true);
    when(repository.save(any(ConfigSet.class))).thenAnswer(invocation -> invocation.getArgument(0));

    ConfigSet updated = useCase.execute(new UpdateConfigSetCommand(
        "cs-1",
        "Config B",
        "tenant-1",
        ConfigSetEnvironmentType.TEST,
        "issuer-b",
        "audience-b",
        "HS256",
        "role",
        "new-secret",
        null,
        30,
        180,
        "https://meet2.example.test",
        "actor-1",
        "trace-1"));

    assertThat(updated.name()).isEqualTo("Config B");
    assertThat(updated.environmentType()).isEqualTo(ConfigSetEnvironmentType.TEST);
    verify(eventPublisher).publishEvent(any(ConfigSetUpdatedEvent.class));
  }

  @Test
  void executeRejectsTenantMismatch() {
    ConfigSet existing = new ConfigSet(
        "cs-1",
        "Config A",
        "tenant-1",
        ConfigSetEnvironmentType.DEV,
        "issuer-a",
        "audience-a",
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
    when(repository.findById("cs-1")).thenReturn(Optional.of(existing));

    assertThatThrownBy(() -> useCase.execute(new UpdateConfigSetCommand(
        "cs-1",
        "Config B",
        "tenant-2",
        ConfigSetEnvironmentType.TEST,
        "issuer-b",
        "audience-b",
        "HS256",
        "role",
        "new-secret",
        null,
        30,
        180,
        "https://meet2.example.test",
        "actor-1",
        "trace-1")))
        .isInstanceOf(ConfigSetNotFoundException.class);
  }
}