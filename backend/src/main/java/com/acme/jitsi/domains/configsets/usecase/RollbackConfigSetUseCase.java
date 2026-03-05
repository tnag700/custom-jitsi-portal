package com.acme.jitsi.domains.configsets.usecase;

import com.acme.jitsi.domains.configsets.event.ConfigSetRollbackCompletedEvent;
import com.acme.jitsi.domains.configsets.service.ConfigCompatibilityCheckResult;
import com.acme.jitsi.domains.configsets.service.ConfigSet;
import com.acme.jitsi.domains.configsets.service.ConfigSetCompatibilityStateService;
import com.acme.jitsi.domains.configsets.service.ConfigSetDryRunValidator;
import com.acme.jitsi.domains.configsets.service.ConfigSetNotFoundException;
import com.acme.jitsi.domains.configsets.service.ConfigSetRepository;
import com.acme.jitsi.domains.configsets.service.ConfigSetRollbackNotAllowedException;
import com.acme.jitsi.domains.configsets.service.ConfigSetRollout;
import com.acme.jitsi.domains.configsets.service.ConfigSetRolloutRepository;
import com.acme.jitsi.domains.configsets.service.RolloutStatus;
import com.acme.jitsi.infrastructure.usecase.UseCase;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RollbackConfigSetUseCase implements UseCase<RollbackConfigSetCommand, ConfigSetRollout> {

  private final ConfigSetRepository configSetRepository;
  private final ConfigSetRolloutRepository rolloutRepository;
  private final ActivateConfigSetUseCase activateConfigSetUseCase;
  private final ConfigSetDryRunValidator configSetDryRunValidator;
  private final ConfigSetCompatibilityStateService compatibilityStateService;
  private final ApplicationEventPublisher eventPublisher;
  private final Clock clock;

  public RollbackConfigSetUseCase(
      ConfigSetRepository configSetRepository,
      ConfigSetRolloutRepository rolloutRepository,
      ActivateConfigSetUseCase activateConfigSetUseCase,
      ConfigSetDryRunValidator configSetDryRunValidator,
      ConfigSetCompatibilityStateService compatibilityStateService,
      ApplicationEventPublisher eventPublisher,
      Clock clock) {
    this.configSetRepository = configSetRepository;
    this.rolloutRepository = rolloutRepository;
    this.activateConfigSetUseCase = activateConfigSetUseCase;
    this.configSetDryRunValidator = configSetDryRunValidator;
    this.compatibilityStateService = compatibilityStateService;
    this.eventPublisher = eventPublisher;
    this.clock = clock;
  }

  @Override
  @Transactional
  public ConfigSetRollout execute(RollbackConfigSetCommand command) {
    ConfigSet target = configSetRepository.findById(command.configSetId())
        .orElseThrow(() -> new ConfigSetNotFoundException(command.configSetId()));

    validateTenantOwnership(command, target);
    ConfigSetRollout sourceRollout = loadSourceRollout(command);
    validateSourceRolloutMatches(command, sourceRollout);
    ConfigSet previousConfig = loadPreviousConfig(sourceRollout);
    validateRollbackCompatibility(command, previousConfig);

    Instant now = Instant.now(clock);
    String currentActiveId = configSetRepository
        .findActiveByTenantIdAndEnvironmentType(command.tenantId(), command.environmentType())
        .map(ConfigSet::configSetId)
        .orElse(command.configSetId());

    ConfigSetRollout applying = new ConfigSetRollout(
        UUID.randomUUID().toString(),
        previousConfig.configSetId(),
        currentActiveId,
        command.tenantId(),
        command.environmentType(),
        RolloutStatus.APPLYING,
        null,
        now,
        null,
        command.actorId(),
        command.traceId());
    rolloutRepository.save(applying);

    activateConfigSetUseCase.execute(new ActivateConfigSetCommand(
        previousConfig.configSetId(),
        command.tenantId(),
        command.actorId(),
        command.traceId()));

    Instant completedAt = Instant.now(clock);

    ConfigSetRollout rolledBack = new ConfigSetRollout(
        applying.rolloutId(),
        applying.configSetId(),
        applying.previousConfigSetId(),
        applying.tenantId(),
        applying.environmentType(),
        RolloutStatus.ROLLED_BACK,
        null,
        applying.startedAt(),
        completedAt,
        applying.actorId(),
        applying.traceId());
    ConfigSetRollout saved = rolloutRepository.save(rolledBack);

    ConfigSetRollout sourceRolledBack = new ConfigSetRollout(
        sourceRollout.rolloutId(),
        sourceRollout.configSetId(),
        sourceRollout.previousConfigSetId(),
        sourceRollout.tenantId(),
        sourceRollout.environmentType(),
        RolloutStatus.ROLLED_BACK,
        sourceRollout.validationErrors(),
        sourceRollout.startedAt(),
        completedAt,
        sourceRollout.actorId(),
        sourceRollout.traceId());
    rolloutRepository.save(sourceRolledBack);

    eventPublisher.publishEvent(new ConfigSetRollbackCompletedEvent(
        saved.rolloutId(),
        saved.configSetId(),
        saved.previousConfigSetId(),
        saved.actorId(),
        saved.traceId()));

    return saved;
  }

  private void validateTenantOwnership(RollbackConfigSetCommand command, ConfigSet target) {
    if (command.tenantId() == null
        || command.tenantId().isBlank()
        || !target.tenantId().equals(command.tenantId().trim())) {
      throw new ConfigSetNotFoundException(command.configSetId());
    }
  }

  private ConfigSetRollout loadSourceRollout(RollbackConfigSetCommand command) {
    return rolloutRepository
        .findLatestSucceededByTenantIdAndEnvironmentType(command.tenantId(), command.environmentType())
        .orElseThrow(() -> new ConfigSetNotFoundException(
            "No successful rollout for tenant '%s' and environment '%s'"
                .formatted(command.tenantId(), command.environmentType())));
  }

  private void validateSourceRolloutMatches(RollbackConfigSetCommand command, ConfigSetRollout sourceRollout) {
    if (!sourceRollout.configSetId().equals(command.configSetId())) {
      throw new ConfigSetNotFoundException(command.configSetId());
    }
  }

  private ConfigSet loadPreviousConfig(ConfigSetRollout sourceRollout) {
    if (sourceRollout.previousConfigSetId() == null || sourceRollout.previousConfigSetId().isBlank()) {
      throw new ConfigSetRollbackNotAllowedException("No previous config set to rollback to");
    }

    return configSetRepository.findById(sourceRollout.previousConfigSetId())
        .orElseThrow(() -> new ConfigSetRollbackNotAllowedException("Previous config set not found or deleted"));
  }

  private void validateRollbackCompatibility(RollbackConfigSetCommand command, ConfigSet previousConfig) {
    String traceId = command.traceId() != null ? command.traceId() : UUID.randomUUID().toString();
    ConfigCompatibilityCheckResult compatibilityResult = configSetDryRunValidator
        .validateCompatibility(previousConfig, traceId);
    compatibilityStateService.record(previousConfig.configSetId(), compatibilityResult);
    if (!compatibilityResult.compatible()) {
      throw new ConfigSetRollbackNotAllowedException(
          "Cannot rollback to incompatible config set: "
              + String.join(", ", compatibilityResult.mismatches().stream().map(m -> m.code().name()).toList()));
    }
  }
}