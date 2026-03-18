package com.acme.jitsi.domains.configsets.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.acme.jitsi.domains.configsets.service.ConfigSetEnvironmentType;
import com.acme.jitsi.domains.configsets.service.ConfigSetDryRunValidator;
import com.acme.jitsi.domains.configsets.service.ConfigSetCompatibilityCheck;
import com.acme.jitsi.domains.configsets.service.ConfigSetCompatibilityStateService;
import com.acme.jitsi.domains.configsets.service.ConfigCompatibilityCheckResult;
import com.acme.jitsi.domains.configsets.service.ConfigCompatibilityMismatch;
import com.acme.jitsi.domains.configsets.service.ConfigCompatibilityMismatchCode;
import com.acme.jitsi.domains.configsets.service.ConfigSet;
import com.acme.jitsi.domains.configsets.service.ConfigSetStatus;
import com.acme.jitsi.domains.configsets.service.ConfigSetRollbackNotAllowedException;
import com.acme.jitsi.domains.configsets.service.ConfigSetRolloutValidationFailedException;
import com.acme.jitsi.domains.configsets.service.ConfigSetRollout;
import com.acme.jitsi.domains.configsets.service.ConfigSetRolloutRepository;
import com.acme.jitsi.domains.configsets.service.ConfigSetService;
import com.acme.jitsi.domains.configsets.service.RolloutStatus;
import com.acme.jitsi.domains.configsets.usecase.ActivateConfigSetUseCase;
import com.acme.jitsi.domains.configsets.usecase.CreateConfigSetUseCase;
import com.acme.jitsi.domains.configsets.usecase.DeactivateConfigSetUseCase;
import com.acme.jitsi.domains.configsets.usecase.RollbackConfigSetUseCase;
import com.acme.jitsi.domains.configsets.usecase.RolloutConfigSetUseCase;
import com.acme.jitsi.domains.configsets.usecase.UpdateConfigSetUseCase;
import com.acme.jitsi.security.ProblemDetailsMappingPolicy;
import com.acme.jitsi.security.ProblemResponseFacade;
import com.acme.jitsi.security.TenantAccessGuard;
import com.acme.jitsi.shared.ErrorCode;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = ConfigSetsController.class)
@Tag("slice")
class ConfigSetsControllerRolloutWebMvcTest {

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private ConfigSetService configSetService;
  @MockitoBean
  private CreateConfigSetUseCase createConfigSetUseCase;
  @MockitoBean
  private UpdateConfigSetUseCase updateConfigSetUseCase;
  @MockitoBean
  private ActivateConfigSetUseCase activateConfigSetUseCase;
  @MockitoBean
  private DeactivateConfigSetUseCase deactivateConfigSetUseCase;
  @MockitoBean
  private RolloutConfigSetUseCase rolloutConfigSetUseCase;
  @MockitoBean
  private RollbackConfigSetUseCase rollbackConfigSetUseCase;
  @MockitoBean
  private ConfigSetRolloutRepository configSetRolloutRepository;
  @MockitoBean
  private ConfigSetDryRunValidator configSetDryRunValidator;
  @MockitoBean
  private ConfigSetCompatibilityStateService compatibilityStateService;
  @MockitoBean
  private ProblemResponseFacade problemResponseFacade;
  @MockitoBean
  private ProblemDetailsMappingPolicy problemDetailsMappingPolicy;
  @MockitoBean
  private TenantAccessGuard tenantAccessGuard;

  @Test
  void rolloutEndpointReturnsSuccessResponse() throws Exception {
    doNothing().when(tenantAccessGuard).assertAccess(anyString(), any());
    when(problemResponseFacade.resolveTraceId(any())).thenReturn("trace-1");
    when(rolloutConfigSetUseCase.execute(any())).thenReturn(new ConfigSetRollout(
        "r-1",
        "cs-1",
        null,
        "tenant-1",
        ConfigSetEnvironmentType.DEV,
        RolloutStatus.SUCCEEDED,
        null,
        Instant.parse("2026-01-01T00:00:00Z"),
        Instant.parse("2026-01-01T00:00:01Z"),
        "actor-1",
        "trace-1"));

    mockMvc.perform(post("/api/v1/config-sets/cs-1/rollout")
            .with(csrf())
            .with(oauth2Login().attributes(attrs -> attrs.put("tenantId", "tenant-1")))
            .header("Idempotency-Key", "idem-rollout-1")
            .param("tenantId", "tenant-1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.rolloutId").value("r-1"))
        .andExpect(jsonPath("$.status").value("SUCCEEDED"));
  }

  @Test
  void latestRolloutEndpointReturnsSuccessResponse() throws Exception {
    doNothing().when(tenantAccessGuard).assertAccess(anyString(), any());
    when(configSetRolloutRepository.findLatestByTenantIdAndEnvironmentType("tenant-1", ConfigSetEnvironmentType.DEV))
        .thenReturn(Optional.of(new ConfigSetRollout(
            "r-2",
            "cs-2",
            "cs-1",
            "tenant-1",
            ConfigSetEnvironmentType.DEV,
            RolloutStatus.APPLYING,
            null,
            Instant.parse("2026-01-01T00:00:00Z"),
            null,
            "actor-1",
            "trace-1")));

    mockMvc.perform(get("/api/v1/config-sets/rollouts/latest")
            .with(csrf())
            .with(oauth2Login().attributes(attrs -> attrs.put("tenantId", "tenant-1")))
            .param("tenantId", "tenant-1")
            .param("environmentType", "DEV"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.rolloutId").value("r-2"))
        .andExpect(jsonPath("$.status").value("APPLYING"));
  }

  @Test
  void rollbackEndpointReturnsSuccessResponse() throws Exception {
    doNothing().when(tenantAccessGuard).assertAccess(anyString(), any());
    when(problemResponseFacade.resolveTraceId(any())).thenReturn("trace-1");
    when(rollbackConfigSetUseCase.execute(any())).thenReturn(new ConfigSetRollout(
        "r-3",
        "cs-prev",
        "cs-cur",
        "tenant-1",
        ConfigSetEnvironmentType.DEV,
        RolloutStatus.ROLLED_BACK,
        null,
        Instant.parse("2026-01-01T00:00:00Z"),
        Instant.parse("2026-01-01T00:00:01Z"),
        "actor-1",
        "trace-1"));

    mockMvc.perform(post("/api/v1/config-sets/cs-cur/rollback")
            .with(csrf())
            .with(oauth2Login().attributes(attrs -> attrs.put("tenantId", "tenant-1")))
            .header("Idempotency-Key", "idem-rollback-1")
            .param("tenantId", "tenant-1")
            .param("environmentType", "DEV"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.rolloutId").value("r-3"))
        .andExpect(jsonPath("$.status").value("ROLLED_BACK"));
  }

  @Test
  void rollbackEndpointReturnsUnprocessableEntityWhenRollbackNotAllowed() throws Exception {
    doNothing().when(tenantAccessGuard).assertAccess(anyString(), any());
    when(problemResponseFacade.resolveTraceId(any())).thenReturn("trace-1");
    when(problemResponseFacade.buildProblemDetail(any(), any(), anyString(), anyString(), anyString()))
        .thenAnswer(invocation -> {
          HttpStatus status = invocation.getArgument(1);
          String detail = invocation.getArgument(3);
          String errorCode = invocation.getArgument(4);
          ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, detail);
          problemDetail.setTitle("Rollback конфиг-набора не разрешён");
          problemDetail.setProperty("errorCode", errorCode);
          problemDetail.setProperty("traceId", "trace-1");
          return problemDetail;
        });
    when(rollbackConfigSetUseCase.execute(any()))
        .thenThrow(new ConfigSetRollbackNotAllowedException("No previous config set to rollback to"));

    mockMvc.perform(post("/api/v1/config-sets/cs-cur/rollback")
            .with(csrf())
            .with(oauth2Login().attributes(attrs -> attrs.put("tenantId", "tenant-1")))
            .header("Idempotency-Key", "idem-rollback-2")
            .param("tenantId", "tenant-1")
            .param("environmentType", "DEV"))
        .andExpect(status().is(422))
      .andExpect(jsonPath("$.errorCode").value(ErrorCode.CONFIG_SET_ROLLBACK_NOT_ALLOWED.code()))
        .andExpect(jsonPath("$.traceId").value("trace-1"));
  }

  @Test
  void rolloutEndpointReturnsConfigIncompatibleWhenValidationFails() throws Exception {
    doNothing().when(tenantAccessGuard).assertAccess(anyString(), any());
    when(problemResponseFacade.resolveTraceId(any())).thenReturn("trace-1");
    when(problemResponseFacade.buildProblemDetail(any(), any(), anyString(), anyString(), anyString()))
        .thenAnswer(invocation -> {
          HttpStatus status = invocation.getArgument(1);
          String detail = invocation.getArgument(3);
          String errorCode = invocation.getArgument(4);
          ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, detail);
          problemDetail.setTitle("Конфиг-набор несовместим");
          problemDetail.setProperty("errorCode", errorCode);
          problemDetail.setProperty("traceId", "trace-1");
          return problemDetail;
        });
    when(rolloutConfigSetUseCase.execute(any()))
        .thenThrow(new ConfigSetRolloutValidationFailedException(java.util.List.of("issuer mismatch")));

    mockMvc.perform(post("/api/v1/config-sets/cs-1/rollout")
            .with(csrf())
            .with(oauth2Login().attributes(attrs -> attrs.put("tenantId", "tenant-1")))
            .header("Idempotency-Key", "idem-rollout-2")
            .param("tenantId", "tenant-1"))
      .andExpect(status().is(422))
        .andExpect(jsonPath("$.errorCode").value(ErrorCode.CONFIG_INCOMPATIBLE.code()))
        .andExpect(jsonPath("$.traceId").value("trace-1"));
  }

      @Test
      void compatibilityEndpointReturnsDiagnosticsPayload() throws Exception {
      doNothing().when(tenantAccessGuard).assertAccess(anyString(), any());
      when(problemResponseFacade.resolveTraceId(any())).thenReturn("trace-compat-1");
      when(configSetService.getById("cs-1")).thenReturn(new ConfigSet(
        "cs-1",
        "Config",
        "tenant-1",
        ConfigSetEnvironmentType.DEV,
        "https://issuer",
        "aud",
        "HS256",
        "role",
        "secret",
        null,
        20,
        120,
        "https://meet.example.test/v2",
        ConfigSetStatus.DRAFT,
        Instant.parse("2026-01-01T00:00:00Z"),
        Instant.parse("2026-01-01T00:00:00Z")));
      when(configSetDryRunValidator.validateCompatibility(any(), anyString())).thenReturn(
        new ConfigCompatibilityCheckResult(
          false,
          java.util.List.of(new ConfigCompatibilityMismatch(
            ConfigCompatibilityMismatchCode.API_VERSION_MISMATCH,
            "meetings API version mismatch",
            "v1",
            "v2")),
          Instant.parse("2026-01-01T00:00:00Z"),
          "trace-compat-1"));
      when(compatibilityStateService.record(anyString(), any())).thenReturn(
          new ConfigSetCompatibilityCheck(
              "chk-1",
              "cs-1",
              false,
              java.util.List.of("API_VERSION_MISMATCH"),
              "meetings API version mismatch",
              Instant.parse("2026-01-01T00:00:00Z"),
              "trace-compat-1"));

      mockMvc.perform(get("/api/v1/config-sets/cs-1/compatibility")
          .with(csrf())
          .with(oauth2Login().attributes(attrs -> attrs.put("tenantId", "tenant-1")))
          .param("tenantId", "tenant-1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("INCOMPATIBLE"))
        .andExpect(jsonPath("$.traceId").value("trace-compat-1"))
        .andExpect(jsonPath("$.mismatches[0].code").value("API_VERSION_MISMATCH"))
        .andExpect(jsonPath("$.mismatches[0].message").value("meetings API version mismatch"))
        .andExpect(jsonPath("$.mismatches[0].expected").value("v1"))
        .andExpect(jsonPath("$.mismatches[0].actual").value("v2"));
      }
}