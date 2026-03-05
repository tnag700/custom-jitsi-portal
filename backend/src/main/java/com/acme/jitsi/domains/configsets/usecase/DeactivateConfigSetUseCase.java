package com.acme.jitsi.domains.configsets.usecase;

import com.acme.jitsi.domains.configsets.event.ConfigSetDeactivatedEvent;
import com.acme.jitsi.domains.configsets.service.ConfigSet;
import com.acme.jitsi.domains.configsets.service.ConfigSetNotFoundException;
import com.acme.jitsi.domains.configsets.service.ConfigSetRepository;
import com.acme.jitsi.domains.configsets.service.ConfigSetStatus;
import com.acme.jitsi.infrastructure.usecase.UseCase;
import java.time.Clock;
import java.time.Instant;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeactivateConfigSetUseCase implements UseCase<DeactivateConfigSetCommand, ConfigSet> {

  private final ConfigSetRepository configSetRepository;
  private final ApplicationEventPublisher eventPublisher;
  private final Clock clock;

  public DeactivateConfigSetUseCase(
      ConfigSetRepository configSetRepository,
      ApplicationEventPublisher eventPublisher,
      Clock clock) {
    this.configSetRepository = configSetRepository;
    this.eventPublisher = eventPublisher;
    this.clock = clock;
  }

  @Override
  @Transactional
  public ConfigSet execute(DeactivateConfigSetCommand command) {
    ConfigSet existing = configSetRepository.findById(command.configSetId())
        .orElseThrow(() -> new ConfigSetNotFoundException(command.configSetId()));

    if (command.tenantId() == null
        || command.tenantId().isBlank()
        || !existing.tenantId().equals(command.tenantId().trim())) {
      throw new ConfigSetNotFoundException(command.configSetId());
    }

    ConfigSet updated = new ConfigSet(
        existing.configSetId(),
        existing.name(),
        existing.tenantId(),
        existing.environmentType(),
        existing.issuer(),
        existing.audience(),
        existing.algorithm(),
        existing.roleClaim(),
        existing.signingSecret(),
        existing.jwksUri(),
        existing.accessTtlMinutes(),
        existing.refreshTtlMinutes(),
        existing.meetingsServiceUrl(),
        ConfigSetStatus.INACTIVE,
        existing.createdAt(),
        Instant.now(clock));

    ConfigSet saved = configSetRepository.save(updated);
    eventPublisher.publishEvent(new ConfigSetDeactivatedEvent(
        saved.configSetId(),
        command.actorId(),
        command.traceId(),
        "status",
        "status=%s".formatted(existing.status()),
        "status=%s".formatted(saved.status())));
    return saved;
  }
}