package com.acme.jitsi.domains.configsets.infrastructure;

import com.acme.jitsi.domains.configsets.service.ConfigSet;
import com.acme.jitsi.domains.configsets.service.ConfigSetEnvironmentType;
import com.acme.jitsi.domains.configsets.service.ConfigSetRepository;
import com.acme.jitsi.domains.configsets.service.ConfigSetStatus;
import com.acme.jitsi.domains.configsets.usecase.CreateConfigSetCommand;
import com.acme.jitsi.domains.configsets.usecase.CreateConfigSetUseCase;
import com.acme.jitsi.domains.configsets.usecase.RolloutConfigSetCommand;
import com.acme.jitsi.domains.configsets.usecase.RolloutConfigSetUseCase;
import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("dev")
@ConditionalOnProperty(
    prefix = "app.dev-bootstrap.active-config-set",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
class DevActiveConfigSetBootstrap implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(DevActiveConfigSetBootstrap.class);
  private static final String ACTOR_ID = "dev-active-config-set-bootstrap";
  private static final int LOOKUP_PAGE = 0;
  private static final int LOOKUP_PAGE_SIZE = 50;

  private final ConfigSetRepository configSetRepository;
  private final CreateConfigSetUseCase createConfigSetUseCase;
  private final RolloutConfigSetUseCase rolloutConfigSetUseCase;
  private final String tenantId;
  private final String configSetName;
  private final String issuer;
  private final String audience;
  private final String algorithm;
  private final String roleClaim;
  private final String signingSecret;
  private final int accessTtlMinutes;
  private final int refreshTtlMinutes;
  private final String openApiServerUrl;
  private final String apiVersion;

  DevActiveConfigSetBootstrap(
      ConfigSetRepository configSetRepository,
      CreateConfigSetUseCase createConfigSetUseCase,
      RolloutConfigSetUseCase rolloutConfigSetUseCase,
      @Value("${app.dev-bootstrap.active-config-set.tenant-id:tenant-1}") String tenantId,
      @Value("${app.dev-bootstrap.active-config-set.name:Local DEV Config}") String configSetName,
      @Value("${app.meetings.token.issuer:jitsi-portal}") String issuer,
      @Value("${app.meetings.token.audience:jitsi-meet}") String audience,
      @Value("${app.meetings.token.algorithm:HS256}") String algorithm,
      @Value("${app.meetings.token.role-claim-name:role}") String roleClaim,
      @Value("${app.meetings.token.signing-secret:}") String signingSecret,
      @Value("${app.meetings.token.ttl-minutes:20}") int accessTtlMinutes,
      @Value("${app.auth.refresh.idle-ttl-minutes:60}") int refreshTtlMinutes,
      @Value("${app.openapi.server-url:http://localhost:8080}") String openApiServerUrl,
      @Value("${app.api.version:v1}") String apiVersion) {
    this.configSetRepository = configSetRepository;
    this.createConfigSetUseCase = createConfigSetUseCase;
    this.rolloutConfigSetUseCase = rolloutConfigSetUseCase;
    this.tenantId = tenantId;
    this.configSetName = configSetName;
    this.issuer = issuer;
    this.audience = audience;
    this.algorithm = algorithm;
    this.roleClaim = roleClaim;
    this.signingSecret = signingSecret;
    this.accessTtlMinutes = accessTtlMinutes;
    this.refreshTtlMinutes = refreshTtlMinutes;
    this.openApiServerUrl = openApiServerUrl;
    this.apiVersion = apiVersion;
  }

  @Override
  public void run(ApplicationArguments args) {
    if (configSetRepository.findActiveByTenantIdAndEnvironmentType(tenantId, ConfigSetEnvironmentType.DEV).isPresent()) {
      return;
    }

    String traceId = UUID.randomUUID().toString();
    ConfigSet draft = findLatestDraftForTenant()
        .orElseGet(() -> createDraftConfigSet(traceId));

    rolloutConfigSetUseCase.execute(new RolloutConfigSetCommand(
        draft.configSetId(),
        tenantId,
        ACTOR_ID,
        traceId));

    log.info(
        "Bootstrapped active DEV config set '{}' for tenant '{}' during local startup.",
        draft.configSetId(),
        tenantId);
  }

  private Optional<ConfigSet> findLatestDraftForTenant() {
    return configSetRepository.findByTenantId(tenantId, LOOKUP_PAGE, LOOKUP_PAGE_SIZE).stream()
        .filter(configSet -> configSet.environmentType() == ConfigSetEnvironmentType.DEV)
        .filter(configSet -> configSet.status() == ConfigSetStatus.DRAFT)
        .max(Comparator.comparing(ConfigSet::updatedAt));
  }

  private ConfigSet createDraftConfigSet(String traceId) {
    return createConfigSetUseCase.execute(new CreateConfigSetCommand(
        configSetName,
        tenantId,
        ConfigSetEnvironmentType.DEV,
        issuer,
        audience,
        algorithm,
        roleClaim,
        signingSecret,
        null,
        accessTtlMinutes,
        refreshTtlMinutes,
        buildMeetingsServiceUrl(),
        ACTOR_ID,
        traceId));
  }

  private String buildMeetingsServiceUrl() {
    String normalizedBaseUrl = openApiServerUrl.endsWith("/")
        ? openApiServerUrl.substring(0, openApiServerUrl.length() - 1)
        : openApiServerUrl;
    String normalizedApiVersion = apiVersion.startsWith("/")
        ? apiVersion.substring(1)
        : apiVersion;
    return normalizedBaseUrl + "/api/" + normalizedApiVersion;
  }
}