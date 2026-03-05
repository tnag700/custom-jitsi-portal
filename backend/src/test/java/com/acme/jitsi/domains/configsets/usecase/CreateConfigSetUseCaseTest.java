package com.acme.jitsi.domains.configsets.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.acme.jitsi.domains.configsets.event.ConfigSetCreatedEvent;
import com.acme.jitsi.domains.configsets.service.ConfigSet;
import com.acme.jitsi.domains.configsets.service.ConfigSetEnvironmentType;
import com.acme.jitsi.domains.configsets.service.ConfigSetInvalidDataException;
import com.acme.jitsi.domains.configsets.service.ConfigSetRepository;
import com.acme.jitsi.domains.configsets.service.ConfigSetStatus;
import com.acme.jitsi.security.JwtAlgorithmPolicy;
import com.acme.jitsi.security.JwtKeySource;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class CreateConfigSetUseCaseTest {

  @Mock
  private ConfigSetRepository repository;
  @Mock
  private JwtAlgorithmPolicy jwtAlgorithmPolicy;
  @Mock
  private ApplicationEventPublisher eventPublisher;

  private CreateConfigSetUseCase useCase;

  @BeforeEach
  void setUp() {
    useCase = new CreateConfigSetUseCase(
        repository,
        jwtAlgorithmPolicy,
        eventPublisher,
        Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
  }

  @Test
  void executeCreatesDraftConfigSetAndPublishesEvent() {
    when(repository.existsByNameAndTenantId("Config A", "tenant-1")).thenReturn(false);
    when(jwtAlgorithmPolicy.isSupportedForKeySource("HS256", JwtKeySource.SECRET)).thenReturn(true);
    when(repository.save(any(ConfigSet.class))).thenAnswer(invocation -> invocation.getArgument(0));

    ConfigSet saved = useCase.execute(new CreateConfigSetCommand(
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
        "actor-1",
        "trace-1"));

    assertThat(saved.status()).isEqualTo(ConfigSetStatus.DRAFT);
    verify(eventPublisher).publishEvent(any(ConfigSetCreatedEvent.class));
  }

  @Test
  void executeRejectsWhenRsAlgorithmWithoutJwksUri() {
    assertThatThrownBy(() -> useCase.execute(new CreateConfigSetCommand(
        "Config A",
        "tenant-1",
        ConfigSetEnvironmentType.DEV,
        "issuer-a",
        "audience-a",
        "RS256",
        "role",
        null,
        null,
        20,
        120,
        "https://meet.example.test",
        "actor-1",
        "trace-1")))
        .isInstanceOf(ConfigSetInvalidDataException.class)
        .hasMessageContaining("Either signingSecret or jwksUri must be provided");
  }
}