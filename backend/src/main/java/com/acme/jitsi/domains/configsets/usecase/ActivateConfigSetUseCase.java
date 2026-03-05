package com.acme.jitsi.domains.configsets.usecase;

import com.acme.jitsi.domains.configsets.event.ConfigSetActivatedEvent;
import com.acme.jitsi.domains.configsets.event.ConfigSetDeactivatedEvent;
import com.acme.jitsi.domains.configsets.service.ConfigSet;
import com.acme.jitsi.domains.configsets.service.ConfigSetActivationNotAllowedException;
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
public class ActivateConfigSetUseCase implements UseCase<ActivateConfigSetCommand, ConfigSet> {

  private final ConfigSetRepository configSetRepository;
  private final ApplicationEventPublisher eventPublisher;
  private final Clock clock;

  public ActivateConfigSetUseCase(
      ConfigSetRepository configSetRepository,
      ApplicationEventPublisher eventPublisher,
      Clock clock) {
    this.configSetRepository = configSetRepository;
    this.eventPublisher = eventPublisher;
    this.clock = clock;
  }

  @Override
  @Transactional
  public ConfigSet execute(ActivateConfigSetCommand command) {
    ConfigSet target = configSetRepository.findById(command.configSetId())
        .orElseThrow(() -> new ConfigSetNotFoundException(command.configSetId()));

    if (command.tenantId() == null
        || command.tenantId().isBlank()
        || !target.tenantId().equals(command.tenantId().trim())) {
      throw new ConfigSetNotFoundException(command.configSetId());
    }

    if (target.status() == ConfigSetStatus.INACTIVE) {
      throw new ConfigSetActivationNotAllowedException(
          "Inactive config-set cannot be activated directly");
    }

    Instant now = Instant.now(clock);
    configSetRepository.findActiveByTenantIdAndEnvironmentType(target.tenantId(), target.environmentType())
        .filter(active -> !active.configSetId().equals(target.configSetId()))
        .ifPresent(active -> {
          ConfigSet deactivated = new ConfigSet(
              active.configSetId(),
              active.name(),
              active.tenantId(),
              active.environmentType(),
              active.issuer(),
              active.audience(),
              active.algorithm(),
              active.roleClaim(),
              active.signingSecret(),
              active.jwksUri(),
              active.accessTtlMinutes(),
              active.refreshTtlMinutes(),
              active.meetingsServiceUrl(),
              ConfigSetStatus.DRAFT,
              active.createdAt(),
              now);
          configSetRepository.save(deactivated);
          eventPublisher.publishEvent(new ConfigSetDeactivatedEvent(
              deactivated.configSetId(),
              command.actorId(),
              command.traceId(),
              "status",
              "status=%s".formatted(active.status()),
              "status=%s".formatted(deactivated.status())));
        });

    ConfigSet activated = new ConfigSet(
        target.configSetId(),
        target.name(),
        target.tenantId(),
        target.environmentType(),
        target.issuer(),
        target.audience(),
        target.algorithm(),
        target.roleClaim(),
        target.signingSecret(),
        target.jwksUri(),
        target.accessTtlMinutes(),
        target.refreshTtlMinutes(),
        target.meetingsServiceUrl(),
        ConfigSetStatus.ACTIVE,
        target.createdAt(),
        now);

    ConfigSet saved = configSetRepository.save(activated);
    eventPublisher.publishEvent(new ConfigSetActivatedEvent(
        saved.configSetId(),
        command.actorId(),
        command.traceId(),
        "status",
        "status=%s".formatted(target.status()),
        "status=%s".formatted(saved.status())));
    return saved;
  }
}