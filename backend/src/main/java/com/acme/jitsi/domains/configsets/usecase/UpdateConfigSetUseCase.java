package com.acme.jitsi.domains.configsets.usecase;

import com.acme.jitsi.domains.configsets.event.ConfigSetUpdatedEvent;
import com.acme.jitsi.domains.configsets.service.ConfigSet;
import com.acme.jitsi.domains.configsets.service.ConfigSetInvalidDataException;
import com.acme.jitsi.domains.configsets.service.ConfigSetNameConflictException;
import com.acme.jitsi.domains.configsets.service.ConfigSetNotFoundException;
import com.acme.jitsi.domains.configsets.service.ConfigSetRepository;
import com.acme.jitsi.infrastructure.usecase.UseCase;
import com.acme.jitsi.security.JwtAlgorithmPolicy;
import java.time.Clock;
import java.time.Instant;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UpdateConfigSetUseCase implements UseCase<UpdateConfigSetCommand, ConfigSet> {

  private final ConfigSetRepository configSetRepository;
  private final ConfigSetCommandSupport support;
  private final ApplicationEventPublisher eventPublisher;
  private final Clock clock;

  public UpdateConfigSetUseCase(
      ConfigSetRepository configSetRepository,
      JwtAlgorithmPolicy jwtAlgorithmPolicy,
      ApplicationEventPublisher eventPublisher,
      Clock clock) {
    this.configSetRepository = configSetRepository;
    this.support = new ConfigSetCommandSupport(jwtAlgorithmPolicy);
    this.eventPublisher = eventPublisher;
    this.clock = clock;
  }

  @Override
  @Transactional
  public ConfigSet execute(UpdateConfigSetCommand command) {
    ConfigSet existing = configSetRepository.findById(command.configSetId())
        .orElseThrow(() -> new ConfigSetNotFoundException(command.configSetId()));

    if (command.tenantId() == null
        || command.tenantId().isBlank()
        || !existing.tenantId().equals(command.tenantId().trim())) {
      throw new ConfigSetNotFoundException(command.configSetId());
    }

    support.validate(
      command.environmentType(),
      command.accessTtlMinutes(),
      command.algorithm(),
      command.signingSecret(),
      command.jwksUri());

    String normalizedName = support.normalizeRequired(command.name(), "Name is required");
    String normalizedTenantId = support.normalizeRequired(command.tenantId(), "Tenant ID is required");
    if (configSetRepository.existsByNameAndTenantIdAndConfigSetIdNot(
        normalizedName,
        normalizedTenantId,
        command.configSetId())) {
      throw new ConfigSetNameConflictException(normalizedName, normalizedTenantId);
    }

    ConfigSet updated = new ConfigSet(
        existing.configSetId(),
        normalizedName,
        normalizedTenantId,
        command.environmentType(),
        support.normalizeRequired(command.issuer(), "Issuer is required"),
        support.normalizeRequired(command.audience(), "Audience is required"),
        support.normalizeAlgorithm(command.algorithm()),
        support.normalizeRoleClaim(command.roleClaim()),
        support.normalizeOptional(command.signingSecret()),
        support.normalizeOptional(command.jwksUri()),
        command.accessTtlMinutes(),
        command.refreshTtlMinutes(),
        support.normalizeAndValidateUrl(command.meetingsServiceUrl(), "Meetings service URL is invalid"),
        existing.status(),
        existing.createdAt(),
        Instant.now(clock));

    ConfigSet saved = configSetRepository.save(updated);
    eventPublisher.publishEvent(new ConfigSetUpdatedEvent(
        saved.configSetId(),
        command.actorId(),
        command.traceId(),
        "name,environmentType,issuer,audience,algorithm,roleClaim,signingSecret,jwksUri,accessTtlMinutes,refreshTtlMinutes,meetingsServiceUrl",
        "name=%s,environmentType=%s,issuer=%s,audience=%s,algorithm=%s,roleClaim=%s,signingSecret=%s,jwksUri=%s,accessTtlMinutes=%s,refreshTtlMinutes=%s,meetingsServiceUrl=%s"
            .formatted(
                existing.name(),
                existing.environmentType(),
                existing.issuer(),
                existing.audience(),
                existing.algorithm(),
                existing.roleClaim(),
                existing.signingSecret() == null ? "-" : "[HIDDEN]",
                existing.jwksUri(),
                existing.accessTtlMinutes(),
                existing.refreshTtlMinutes(),
                existing.meetingsServiceUrl()),
        "name=%s,environmentType=%s,issuer=%s,audience=%s,algorithm=%s,roleClaim=%s,signingSecret=%s,jwksUri=%s,accessTtlMinutes=%s,refreshTtlMinutes=%s,meetingsServiceUrl=%s"
            .formatted(
                saved.name(),
                saved.environmentType(),
                saved.issuer(),
                saved.audience(),
                saved.algorithm(),
                saved.roleClaim(),
                saved.signingSecret() == null ? "-" : "[CHANGED]",
                saved.jwksUri(),
                saved.accessTtlMinutes(),
                saved.refreshTtlMinutes(),
                saved.meetingsServiceUrl())));
    return saved;
  }
}