package com.acme.jitsi.domains.configsets.usecase;

import com.acme.jitsi.domains.configsets.event.ConfigSetCompatibilityCheckedEvent;
import com.acme.jitsi.domains.configsets.event.ConfigSetRolloutCompletedEvent;
import com.acme.jitsi.domains.configsets.service.ConfigCompatibilityCheckResult;
import com.acme.jitsi.domains.configsets.service.ConfigSet;
import com.acme.jitsi.domains.configsets.service.ConfigSetCompatibilityStateService;
import com.acme.jitsi.domains.configsets.service.ConfigSetDryRunValidator;
import com.acme.jitsi.domains.configsets.service.ConfigSetNotFoundException;
import com.acme.jitsi.domains.configsets.service.ConfigSetRepository;
import com.acme.jitsi.domains.configsets.service.ConfigSetRollout;
import com.acme.jitsi.domains.configsets.service.ConfigSetRolloutNotAllowedException;
import com.acme.jitsi.domains.configsets.service.ConfigSetRolloutRepository;
import com.acme.jitsi.domains.configsets.service.ConfigSetRolloutValidationFailedException;
import com.acme.jitsi.domains.configsets.service.ConfigSetStatus;
import com.acme.jitsi.domains.configsets.service.DryRunResult;
import com.acme.jitsi.domains.configsets.service.RolloutStatus;
import com.acme.jitsi.infrastructure.usecase.UseCase;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RolloutConfigSetUseCase implements UseCase<RolloutConfigSetCommand, ConfigSetRollout> {

  private final ConfigSetRepository configSetRepository;
  private final ConfigSetRolloutRepository rolloutRepository;
  private final ConfigSetDryRunValidator dryRunValidator;
  private final ConfigSetCompatibilityStateService compatibilityStateService;
  private final ActivateConfigSetUseCase activateConfigSetUseCase;
  private final ApplicationEventPublisher eventPublisher;
  private final Clock clock;

  public RolloutConfigSetUseCase(
      ConfigSetRepository configSetRepository,
      ConfigSetRolloutRepository rolloutRepository,
      ConfigSetDryRunValidator dryRunValidator,
      ConfigSetCompatibilityStateService compatibilityStateService,
      ActivateConfigSetUseCase activateConfigSetUseCase,
      ApplicationEventPublisher eventPublisher,
      Clock clock) {
    this.configSetRepository = configSetRepository;
    this.rolloutRepository = rolloutRepository;
    this.dryRunValidator = dryRunValidator;
    this.compatibilityStateService = compatibilityStateService;
    this.activateConfigSetUseCase = activateConfigSetUseCase;
    this.eventPublisher = eventPublisher;
    this.clock = clock;
  }

  @Override
  @Transactional(noRollbackFor = ConfigSetRolloutValidationFailedException.class)
  public ConfigSetRollout execute(RolloutConfigSetCommand command) {
    ConfigSet target = configSetRepository.findById(command.configSetId())
        .orElseThrow(() -> new ConfigSetNotFoundException(command.configSetId()));

    if (command.tenantId() == null
        || command.tenantId().isBlank()
        || !target.tenantId().equals(command.tenantId().trim())) {
      throw new ConfigSetNotFoundException(command.configSetId());
    }

    if (target.status() != ConfigSetStatus.DRAFT) {
      throw new ConfigSetRolloutNotAllowedException("Only DRAFT config-set can be rolled out");
    }

    Instant now = Instant.now(clock);
    ConfigSetRollout pending = new ConfigSetRollout(
        UUID.randomUUID().toString(),
        target.configSetId(),
        null,
        target.tenantId(),
        target.environmentType(),
        RolloutStatus.PENDING,
        null,
        now,
        null,
        command.actorId(),
        command.traceId());
    rolloutRepository.save(pending);

    ConfigSetRollout validating = updateStatus(pending, RolloutStatus.VALIDATING, null, null, null);
    rolloutRepository.save(validating);

    ConfigCompatibilityCheckResult compatibilityResult = dryRunValidator
      .validateCompatibility(target, command.traceId());
    DryRunResult dryRunResult = DryRunResult.fromCompatibilityResult(compatibilityResult);
    compatibilityStateService.record(target.configSetId(), compatibilityResult);

    eventPublisher.publishEvent(new ConfigSetCompatibilityCheckedEvent(
      target.configSetId(),
      command.actorId(),
      compatibilityResult.traceId(),
      compatibilityResult.compatible(),
      compatibilityResult.mismatches().stream().map(mismatch -> mismatch.code().name()).toList(),
      String.join("; ", dryRunResult.errors())));

    if (!dryRunResult.valid()) {
      ConfigSetRollout failed = updateStatus(
          validating,
          RolloutStatus.FAILED,
          String.join("; ", dryRunResult.errors()),
          null,
          Instant.now(clock));
      rolloutRepository.save(failed);
      throw new ConfigSetRolloutValidationFailedException(dryRunResult.errors());
    }

    String previousConfigSetId = configSetRepository
        .findActiveByTenantIdAndEnvironmentType(target.tenantId(), target.environmentType())
        .filter(active -> !active.configSetId().equals(target.configSetId()))
        .map(ConfigSet::configSetId)
        .orElse(null);

    ConfigSetRollout applying = updateStatus(
        validating,
        RolloutStatus.APPLYING,
        null,
        previousConfigSetId,
        null);
    rolloutRepository.save(applying);

    activateConfigSetUseCase.execute(new ActivateConfigSetCommand(
        target.configSetId(),
        target.tenantId(),
        command.actorId(),
        command.traceId()));

    ConfigSetRollout succeeded = updateStatus(
        applying,
        RolloutStatus.SUCCEEDED,
        null,
        previousConfigSetId,
        Instant.now(clock));
    ConfigSetRollout saved = rolloutRepository.save(succeeded);

    eventPublisher.publishEvent(new ConfigSetRolloutCompletedEvent(
        saved.rolloutId(),
        saved.configSetId(),
        saved.previousConfigSetId(),
        saved.actorId(),
        saved.traceId(),
        saved.status().name()));

    return saved;
  }

  private ConfigSetRollout updateStatus(
      ConfigSetRollout source,
      RolloutStatus status,
      String validationErrors,
      String previousConfigSetId,
      Instant completedAt) {
    return new ConfigSetRollout(
        source.rolloutId(),
        source.configSetId(),
        previousConfigSetId != null ? previousConfigSetId : source.previousConfigSetId(),
        source.tenantId(),
        source.environmentType(),
        status,
        validationErrors,
        source.startedAt(),
        completedAt,
        source.actorId(),
        source.traceId());
  }
}