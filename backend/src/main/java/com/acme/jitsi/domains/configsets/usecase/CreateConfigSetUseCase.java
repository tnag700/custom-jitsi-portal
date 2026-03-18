package com.acme.jitsi.domains.configsets.usecase;

import com.acme.jitsi.domains.configsets.event.ConfigSetCreatedEvent;
import com.acme.jitsi.domains.configsets.service.ConfigSet;
import com.acme.jitsi.domains.configsets.service.ConfigSetNameConflictException;
import com.acme.jitsi.domains.configsets.service.ConfigSetRepository;
import com.acme.jitsi.domains.configsets.service.ConfigSetStatus;
import com.acme.jitsi.infrastructure.usecase.UseCase;
import com.acme.jitsi.security.JwtAlgorithmPolicy;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreateConfigSetUseCase implements UseCase<CreateConfigSetCommand, ConfigSet> {

  private final ConfigSetRepository configSetRepository;
  private final ConfigSetCommandSupport support;
  private final ApplicationEventPublisher eventPublisher;
  private final Clock clock;

  public CreateConfigSetUseCase(
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
  public ConfigSet execute(CreateConfigSetCommand command) {
    support.validate(
        command.environmentType(),
        command.accessTtlMinutes(),
        command.algorithm(),
        command.signingSecret(),
        command.jwksUri());

    String normalizedName = support.normalizeRequired(command.name(), "Name is required");
    String normalizedTenantId = support.normalizeRequired(command.tenantId(), "Tenant ID is required");
    if (configSetRepository.existsByNameAndTenantId(normalizedName, normalizedTenantId)) {
      throw new ConfigSetNameConflictException(normalizedName, normalizedTenantId);
    }

    Instant now = Instant.now(clock);
    ConfigSet configSet = new ConfigSet(
        UUID.randomUUID().toString(),
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
        ConfigSetStatus.DRAFT,
        now,
        now);

    ConfigSet saved = configSetRepository.save(configSet);
    eventPublisher.publishEvent(new ConfigSetCreatedEvent(
        saved.configSetId(),
        command.actorId(),
        command.traceId(),
        "name,tenantId,environmentType,issuer,audience,algorithm,roleClaim,signingSecret,jwksUri,accessTtlMinutes,refreshTtlMinutes,meetingsServiceUrl,status",
        "-",
        "name=%s,tenantId=%s,environmentType=%s,issuer=%s,audience=%s,algorithm=%s,roleClaim=%s,signingSecret=%s,jwksUri=%s,accessTtlMinutes=%s,refreshTtlMinutes=%s,meetingsServiceUrl=%s,status=%s"
            .formatted(
                saved.name(),
                saved.tenantId(),
                saved.environmentType(),
                saved.issuer(),
                saved.audience(),
                saved.algorithm(),
                saved.roleClaim(),
                saved.signingSecret() == null ? "-" : "[CHANGED]",
                saved.jwksUri(),
                saved.accessTtlMinutes(),
                saved.refreshTtlMinutes(),
                saved.meetingsServiceUrl(),
                saved.status())));
    return saved;
  }
}