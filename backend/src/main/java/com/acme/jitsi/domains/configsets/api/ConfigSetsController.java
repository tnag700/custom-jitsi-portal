package com.acme.jitsi.domains.configsets.api;

import com.acme.jitsi.domains.configsets.service.ConfigSet;
import com.acme.jitsi.domains.configsets.service.ConfigCompatibilityCheckResult;
import com.acme.jitsi.domains.configsets.service.ConfigSetCompatibilityCheck;
import com.acme.jitsi.domains.configsets.service.ConfigSetCompatibilityStateService;
import com.acme.jitsi.domains.configsets.service.ConfigSetEnvironmentType;
import com.acme.jitsi.domains.configsets.service.ConfigSetDryRunValidator;
import com.acme.jitsi.domains.configsets.service.ConfigSetNotFoundException;
import com.acme.jitsi.domains.configsets.service.ConfigSetRollout;
import com.acme.jitsi.domains.configsets.service.ConfigSetRolloutRepository;
import com.acme.jitsi.domains.configsets.service.ConfigSetService;
import com.acme.jitsi.domains.configsets.usecase.ActivateConfigSetCommand;
import com.acme.jitsi.domains.configsets.usecase.ActivateConfigSetUseCase;
import com.acme.jitsi.domains.configsets.usecase.CreateConfigSetCommand;
import com.acme.jitsi.domains.configsets.usecase.CreateConfigSetUseCase;
import com.acme.jitsi.domains.configsets.usecase.DeactivateConfigSetCommand;
import com.acme.jitsi.domains.configsets.usecase.DeactivateConfigSetUseCase;
import com.acme.jitsi.domains.configsets.usecase.RollbackConfigSetCommand;
import com.acme.jitsi.domains.configsets.usecase.RollbackConfigSetUseCase;
import com.acme.jitsi.domains.configsets.usecase.RolloutConfigSetCommand;
import com.acme.jitsi.domains.configsets.usecase.RolloutConfigSetUseCase;
import com.acme.jitsi.domains.configsets.usecase.UpdateConfigSetCommand;
import com.acme.jitsi.domains.configsets.usecase.UpdateConfigSetUseCase;
import com.acme.jitsi.infrastructure.idempotency.Idempotent;
import com.acme.jitsi.security.ProblemResponseFacade;
import com.acme.jitsi.security.TenantAccessGuard;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/config-sets", version = "v1")
class ConfigSetsController {

  private final ConfigSetService configSetService;
  private final CreateConfigSetUseCase createConfigSetUseCase;
  private final UpdateConfigSetUseCase updateConfigSetUseCase;
  private final ActivateConfigSetUseCase activateConfigSetUseCase;
  private final DeactivateConfigSetUseCase deactivateConfigSetUseCase;
  private final RolloutConfigSetUseCase rolloutConfigSetUseCase;
  private final RollbackConfigSetUseCase rollbackConfigSetUseCase;
  private final ConfigSetRolloutRepository configSetRolloutRepository;
  private final ConfigSetDryRunValidator configSetDryRunValidator;
  private final ConfigSetCompatibilityStateService compatibilityStateService;
  private final ProblemResponseFacade problemResponseFacade;
  private final TenantAccessGuard tenantAccessGuard;

  ConfigSetsController(
      ConfigSetService configSetService,
      CreateConfigSetUseCase createConfigSetUseCase,
      UpdateConfigSetUseCase updateConfigSetUseCase,
      ActivateConfigSetUseCase activateConfigSetUseCase,
      DeactivateConfigSetUseCase deactivateConfigSetUseCase,
      RolloutConfigSetUseCase rolloutConfigSetUseCase,
      RollbackConfigSetUseCase rollbackConfigSetUseCase,
      ConfigSetRolloutRepository configSetRolloutRepository,
      ConfigSetDryRunValidator configSetDryRunValidator,
      ConfigSetCompatibilityStateService compatibilityStateService,
      ProblemResponseFacade problemResponseFacade,
      TenantAccessGuard tenantAccessGuard) {
    this.configSetService = configSetService;
    this.createConfigSetUseCase = createConfigSetUseCase;
    this.updateConfigSetUseCase = updateConfigSetUseCase;
    this.activateConfigSetUseCase = activateConfigSetUseCase;
    this.deactivateConfigSetUseCase = deactivateConfigSetUseCase;
    this.rolloutConfigSetUseCase = rolloutConfigSetUseCase;
    this.rollbackConfigSetUseCase = rollbackConfigSetUseCase;
    this.configSetRolloutRepository = configSetRolloutRepository;
    this.configSetDryRunValidator = configSetDryRunValidator;
    this.compatibilityStateService = compatibilityStateService;
    this.problemResponseFacade = problemResponseFacade;
    this.tenantAccessGuard = tenantAccessGuard;
  }

  @Idempotent
  @PostMapping
  ResponseEntity<ConfigSetResponse> create(
      @Valid @RequestBody CreateConfigSetRequest request,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @AuthenticationPrincipal OAuth2User principal,
      HttpServletRequest httpRequest) {
    tenantAccessGuard.assertAccess(request.tenantId(), principal);
    String traceId = problemResponseFacade.resolveTraceId(httpRequest);
    ConfigSet saved = createConfigSetUseCase.execute(new CreateConfigSetCommand(
        request.name(),
        request.tenantId(),
        request.environmentType(),
        request.issuer(),
        request.audience(),
        request.algorithm(),
        request.roleClaim(),
        request.signingSecret(),
        request.jwksUri(),
        request.accessTtlMinutes(),
        request.refreshTtlMinutes(),
        request.meetingsServiceUrl(),
        principal.getName(),
        traceId));
      return ResponseEntity
        .created(ServletUriComponentsBuilder
          .fromCurrentRequest()
          .path("/{configSetId}")
          .buildAndExpand(saved.configSetId())
          .toUri())
        .body(toResponse(saved));
  }

      @Idempotent
  @PutMapping("/{configSetId}")
  ConfigSetResponse update(
      @PathVariable("configSetId") String configSetId,
      @Valid @RequestBody UpdateConfigSetRequest request,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @AuthenticationPrincipal OAuth2User principal,
      HttpServletRequest httpRequest) {
    tenantAccessGuard.assertAccess(request.tenantId(), principal);
    String traceId = problemResponseFacade.resolveTraceId(httpRequest);
    ConfigSet saved = updateConfigSetUseCase.execute(new UpdateConfigSetCommand(
        configSetId,
        request.name(),
        request.tenantId(),
        request.environmentType(),
        request.issuer(),
        request.audience(),
        request.algorithm(),
        request.roleClaim(),
        request.signingSecret(),
        request.jwksUri(),
        request.accessTtlMinutes(),
        request.refreshTtlMinutes(),
        request.meetingsServiceUrl(),
        principal.getName(),
        traceId));
    return toResponse(saved);
  }

  @Idempotent
  @PostMapping("/{configSetId}/activate")
  ConfigSetResponse activate(
      @PathVariable("configSetId") String configSetId,
      @RequestParam("tenantId") String tenantId,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @AuthenticationPrincipal OAuth2User principal,
      HttpServletRequest httpRequest) {
    tenantAccessGuard.assertAccess(tenantId, principal);
    String traceId = problemResponseFacade.resolveTraceId(httpRequest);
    ConfigSet saved = activateConfigSetUseCase.execute(
        new ActivateConfigSetCommand(configSetId, tenantId, principal.getName(), traceId));
    return toResponse(saved);
  }

  @Idempotent
  @PostMapping("/{configSetId}/deactivate")
  ConfigSetResponse deactivate(
      @PathVariable("configSetId") String configSetId,
      @RequestParam("tenantId") String tenantId,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @AuthenticationPrincipal OAuth2User principal,
      HttpServletRequest httpRequest) {
    tenantAccessGuard.assertAccess(tenantId, principal);
    String traceId = problemResponseFacade.resolveTraceId(httpRequest);
    ConfigSet saved = deactivateConfigSetUseCase.execute(
        new DeactivateConfigSetCommand(configSetId, tenantId, principal.getName(), traceId));
    return toResponse(saved);
  }

  @GetMapping("/{configSetId}")
  ConfigSetResponse getById(
      @PathVariable("configSetId") String configSetId,
      @AuthenticationPrincipal OAuth2User principal) {
    ConfigSet configSet = configSetService.getById(configSetId);
    tenantAccessGuard.assertAccess(configSet.tenantId(), principal);
    return toResponse(configSet);
  }

  @GetMapping
  PagedConfigSetResponse listByTenant(
      @RequestParam("tenantId") String tenantId,
      @RequestParam(name = "page", defaultValue = "0") int page,
      @RequestParam(name = "size", defaultValue = "20") int size,
      @AuthenticationPrincipal OAuth2User principal) {
    int effectiveSize = size <= 0 ? 20 : size;
    tenantAccessGuard.assertAccess(tenantId, principal);
    List<ConfigSetResponse> content = configSetService.listByTenant(tenantId, page, effectiveSize)
        .stream()
        .map(ConfigSetsController::toResponse)
        .toList();
    long totalElements = configSetService.countByTenant(tenantId);
    int totalPages = (int) Math.ceil((double) totalElements / effectiveSize);
    return new PagedConfigSetResponse(content, page, effectiveSize, totalElements, totalPages);
  }

  @GetMapping("/active")
  ConfigSetResponse getActiveForEnvironment(
      @RequestParam("tenantId") String tenantId,
      @RequestParam("environmentType") ConfigSetEnvironmentType environmentType,
      @AuthenticationPrincipal OAuth2User principal) {
    tenantAccessGuard.assertAccess(tenantId, principal);
    return toResponse(configSetService.getActiveForEnvironment(tenantId, environmentType));
  }

  @Idempotent
  @PostMapping("/{configSetId}/rollout")
  ConfigSetRolloutResponse rollout(
      @PathVariable("configSetId") String configSetId,
      @RequestParam("tenantId") String tenantId,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @AuthenticationPrincipal OAuth2User principal,
      HttpServletRequest httpRequest) {
    tenantAccessGuard.assertAccess(tenantId, principal);
    String traceId = problemResponseFacade.resolveTraceId(httpRequest);
    ConfigSetRollout rollout = rolloutConfigSetUseCase.execute(
        new RolloutConfigSetCommand(configSetId, tenantId, principal.getName(), traceId));
    return toRolloutResponse(rollout);
  }

  @Idempotent
  @PostMapping("/{configSetId}/rollback")
  ConfigSetRolloutResponse rollback(
      @PathVariable("configSetId") String configSetId,
      @RequestParam("tenantId") String tenantId,
      @RequestParam("environmentType") ConfigSetEnvironmentType environmentType,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @AuthenticationPrincipal OAuth2User principal,
      HttpServletRequest httpRequest) {
    tenantAccessGuard.assertAccess(tenantId, principal);
    String traceId = problemResponseFacade.resolveTraceId(httpRequest);
    ConfigSetRollout rollout = rollbackConfigSetUseCase.execute(new RollbackConfigSetCommand(
        configSetId,
        tenantId,
        environmentType,
        principal.getName(),
        traceId));
    return toRolloutResponse(rollout);
  }

  @GetMapping("/rollouts/latest")
  ConfigSetRolloutResponse getLatestRollout(
      @RequestParam("tenantId") String tenantId,
      @RequestParam("environmentType") ConfigSetEnvironmentType environmentType,
      @AuthenticationPrincipal OAuth2User principal) {
    tenantAccessGuard.assertAccess(tenantId, principal);
    ConfigSetRollout rollout = configSetRolloutRepository
        .findLatestByTenantIdAndEnvironmentType(tenantId, environmentType)
        .orElseThrow(() -> new ConfigSetNotFoundException(
            "No rollout found for tenant '%s' and environment '%s'"
                .formatted(tenantId, environmentType)));
    return toRolloutResponse(rollout);
  }

  @GetMapping("/{configSetId}/compatibility")
  ConfigSetCompatibilityResponse getCompatibility(
      @PathVariable("configSetId") String configSetId,
      @RequestParam("tenantId") String tenantId,
      @AuthenticationPrincipal OAuth2User principal,
      HttpServletRequest httpRequest) {
    tenantAccessGuard.assertAccess(tenantId, principal);
    ConfigSet configSet = configSetService.getById(configSetId);
    tenantAccessGuard.assertAccess(configSet.tenantId(), principal);
    String traceId = problemResponseFacade.resolveTraceId(httpRequest);
    ConfigCompatibilityCheckResult result = configSetDryRunValidator.validateCompatibility(configSet, traceId);
    ConfigSetCompatibilityCheck snapshot = compatibilityStateService.record(configSetId, result);
    return new ConfigSetCompatibilityResponse(
      snapshot.compatible() ? "COMPATIBLE" : "INCOMPATIBLE",
      result.mismatches().stream().map(mismatch -> new ConfigSetCompatibilityMismatchResponse(
          mismatch.code().name(),
          mismatch.message(),
          mismatch.expected(),
          mismatch.actual())).toList(),
      snapshot.checkedAt().toString(),
      snapshot.traceId());
  }

  private static ConfigSetResponse toResponse(ConfigSet configSet) {
    return new ConfigSetResponse(
        configSet.configSetId(),
        configSet.name(),
        configSet.tenantId(),
        configSet.environmentType().name().toLowerCase(),
        configSet.issuer(),
        configSet.audience(),
        configSet.algorithm(),
        configSet.roleClaim(),
        configSet.signingSecret() == null ? null : "***",
        configSet.jwksUri(),
        configSet.accessTtlMinutes(),
        configSet.refreshTtlMinutes(),
        configSet.meetingsServiceUrl(),
        configSet.status().name().toLowerCase(),
        configSet.createdAt(),
        configSet.updatedAt());
  }

  private static ConfigSetRolloutResponse toRolloutResponse(ConfigSetRollout rollout) {
    return new ConfigSetRolloutResponse(
        rollout.rolloutId(),
        rollout.configSetId(),
        rollout.previousConfigSetId(),
        rollout.tenantId(),
        rollout.environmentType().name(),
        rollout.status().name(),
        rollout.validationErrors(),
        rollout.startedAt() == null ? null : rollout.startedAt().toString(),
        rollout.completedAt() == null ? null : rollout.completedAt().toString(),
        rollout.actorId());
  }
}