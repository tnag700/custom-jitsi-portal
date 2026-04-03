package com.acme.jitsi.domains.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.acme.jitsi.domains.admin.dto.AdminDashboardSummaryResponse;
import com.acme.jitsi.domains.configsets.service.ConfigSet;
import com.acme.jitsi.domains.configsets.service.ConfigSetCompatibilityCheck;
import com.acme.jitsi.domains.configsets.service.ConfigSetCompatibilityStateService;
import com.acme.jitsi.domains.configsets.service.ConfigSetEnvironmentType;
import com.acme.jitsi.domains.configsets.service.ConfigSetRepository;
import com.acme.jitsi.domains.configsets.service.ConfigSetStatus;
import com.acme.jitsi.domains.health.dto.HealthResponse;
import com.acme.jitsi.domains.health.dto.JoinReadinessCheckResponse;
import com.acme.jitsi.domains.health.dto.JoinReadinessResponse;
import com.acme.jitsi.domains.health.service.HealthService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminDashboardServiceTest {

  @Mock
  private HealthService healthService;

  @Mock
  private ConfigSetRepository configSetRepository;

  @Mock
  private ConfigSetCompatibilityStateService compatibilityStateService;

  @Mock
  private AdminDashboardReadModel readModel;

  private AdminDashboardService service;

  @BeforeEach
  void setUp() {
    Clock clock = Clock.fixed(Instant.parse("2026-03-18T10:00:00Z"), ZoneOffset.UTC);
    service = new AdminDashboardService(
        healthService,
        configSetRepository,
        compatibilityStateService,
        readModel,
        clock,
        "https://logs.example.test/trace/{traceId}",
        "https://ops.example.test/incidents/current");
  }

  @Test
  void aggregatesExistingSignalsIntoDashboardSummary() {
    ConfigSet activeConfig = new ConfigSet(
        "config-dev-1",
        "Dev Config",
        "tenant-1",
        ConfigSetEnvironmentType.DEV,
        "issuer",
        "audience",
        "HS256",
        "role",
        null,
        null,
        15,
        60,
        "https://meetings.example.test",
        ConfigSetStatus.ACTIVE,
        Instant.parse("2026-03-18T08:00:00Z"),
        Instant.parse("2026-03-18T09:00:00Z"));
    ConfigSetCompatibilityCheck compatibilityCheck = new ConfigSetCompatibilityCheck(
        "check-1",
        "config-dev-1",
        false,
        List.of("ROLE_CLAIM_MISMATCH"),
        "Role mismatch",
        Instant.parse("2026-03-18T09:30:00Z"),
        "trace-config-1");

    when(configSetRepository.findActiveByTenantIdAndEnvironmentType("tenant-1", ConfigSetEnvironmentType.DEV))
        .thenReturn(Optional.of(activeConfig));
    when(healthService.getHealth()).thenReturn(new HealthResponse("DOWN", "INCOMPATIBLE", "config-dev-1", "Role mismatch", "trace-health-1", "2026-03-18T09:31:00Z"));
    when(healthService.getJoinReadiness("trace-dashboard-1")).thenReturn(new JoinReadinessResponse(
        "blocked",
        "2026-03-18T09:32:00Z",
        "trace-dashboard-1",
        "https://portal.example.test/join/demo",
        List.of(new JoinReadinessCheckResponse("backend", "error", "Blocked", "Config mismatch", List.of("Rollback"), "CONFIG_INCOMPATIBLE", true))));
    when(compatibilityStateService.findLatestByConfigSetId("config-dev-1")).thenReturn(Optional.of(compatibilityCheck));
    when(readModel.loadJoinAuditOverview(any())).thenReturn(new AdminDashboardReadModel.JoinAuditOverview(
        7,
        3,
        List.of(new AdminDashboardReadModel.Count("CONFIG", 2), new AdminDashboardReadModel.Count("TOKEN", 1)),
        List.of(new AdminDashboardReadModel.Count("CONFIG_INCOMPATIBLE", 2), new AdminDashboardReadModel.Count("TOKEN_INVALID", 1)),
        List.of(new AdminDashboardReadModel.JoinAuditRecord(
            Instant.parse("2026-03-18T09:55:00Z"),
            "room-1",
            "meeting-1",
            "subject-1",
            "trace-join-1",
            "CONFIG_INCOMPATIBLE",
            "CONFIG",
            "Активный конфиг-контур несовместим с требованиями входа во встречу.")),
        false));

    var response = service.getSummary("tenant-1", "15m", "dev", "room-1", "meeting-1", "trace-dashboard-1");

    assertThat(response.environment()).isEqualTo("dev");
    assertThat(response.priorityBanner().active()).isTrue();
    assertThat(response.priorityBanner().handoff().errorCode()).isEqualTo("CONFIG_INCOMPATIBLE");
    assertThat(response.topDegradations()).extracting(AdminDashboardSummaryResponse.DegradationSummary::id)
        .contains("config-compatibility", "failure-spike");
    assertThat(response.keyServiceStatuses()).extracting(AdminDashboardSummaryResponse.ServiceStatus::key)
        .contains("backend-api", "meeting-join-surface");
    assertThat(response.latestSpikes()).extracting(AdminDashboardSummaryResponse.LatestSpike::errorCode)
        .containsExactly("CONFIG_INCOMPATIBLE", "TOKEN_INVALID");
    assertThat(response.affectedScopeSummary()).extracting(AdminDashboardSummaryResponse.AffectedScopeSummary::scopeValue)
        .contains("room-1", "meeting-1");
        assertThat(response.affectedScopeSummary()).anySatisfy(summary -> {
            if ("room".equals(summary.scopeType())) {
                assertThat(summary.handoff().roomId()).isEqualTo("room-1");
                assertThat(summary.handoff().meetingId()).isEqualTo("meeting-1");
            }
        });
    assertThat(response.safeStateSummary().stable()).isFalse();
  }

  @Test
  void passesSelectedEnvironmentAndWindowToReadModel() {
    when(configSetRepository.findActiveByTenantIdAndEnvironmentType("tenant-1", ConfigSetEnvironmentType.PROD))
        .thenReturn(Optional.empty());
    when(healthService.getHealth()).thenReturn(new HealthResponse("UP", "COMPATIBLE", null, null, null, null));
    when(healthService.getJoinReadiness("trace-dashboard-2")).thenReturn(new JoinReadinessResponse(
        "ready",
        "2026-03-18T10:00:00Z",
        "trace-dashboard-2",
        null,
        List.of()));
    when(readModel.loadJoinAuditOverview(any())).thenReturn(new AdminDashboardReadModel.JoinAuditOverview(0, 0, List.of(), List.of(), List.of(), false));

    service.getSummary("tenant-1", "24h", "prod", null, null, "trace-dashboard-2");

    ArgumentCaptor<AdminDashboardReadModel.DashboardFilter> captor = ArgumentCaptor.forClass(AdminDashboardReadModel.DashboardFilter.class);
    verify(readModel).loadJoinAuditOverview(captor.capture());
    assertThat(captor.getValue().environmentType()).isEqualTo(ConfigSetEnvironmentType.PROD);
    assertThat(captor.getValue().from()).isEqualTo(Instant.parse("2026-03-17T10:00:00Z"));
  }

    @Test
    void returnsActionOrientedSafeStateWhenNoSignalsRequireImmediateTriage() {
        when(configSetRepository.findActiveByTenantIdAndEnvironmentType("tenant-1", ConfigSetEnvironmentType.DEV))
                .thenReturn(Optional.empty());
        when(healthService.getHealth()).thenReturn(new HealthResponse("UP", "COMPATIBLE", null, null, null, null));
        when(healthService.getJoinReadiness("trace-dashboard-safe")).thenReturn(new JoinReadinessResponse(
                "ready",
                "2026-03-18T10:00:00Z",
                "trace-dashboard-safe",
                "https://portal.example.test/join/demo",
                List.of()));
        when(readModel.loadJoinAuditOverview(any())).thenReturn(new AdminDashboardReadModel.JoinAuditOverview(
                4,
                0,
                List.of(),
                List.of(),
                List.of(),
                false));

        var response = service.getSummary("tenant-1", "15m", "dev", null, null, "trace-dashboard-safe");

        assertThat(response.priorityBanner().active()).isFalse();
        assertThat(response.topDegradations()).isEmpty();
        assertThat(response.safeStateSummary().stable()).isTrue();
        assertThat(response.safeStateSummary().actions()).extracting(AdminDashboardSummaryResponse.SafeStateAction::href)
            .contains("/admin/incidents?environment=dev&period=15m", "/admin/role-history?environment=dev", "/admin/config-sets?environment=dev");
    }

  @Test
  void buildsTraceLinksForDrillDownSamples() {
    when(readModel.loadDrillDown(any())).thenReturn(new AdminDashboardReadModel.DrillDownOverview(
        "errorCode",
        "TOKEN_INVALID",
        1,
        List.of(new AdminDashboardReadModel.JoinAuditRecord(
            Instant.parse("2026-03-18T09:59:00Z"),
            "room-1",
            "meeting-1",
            "subject-1",
            "trace-join-1",
            "TOKEN_INVALID",
            "TOKEN",
            "Повторите вход через SSO")),
        false));

    var response = service.getDrillDown("tenant-1", "15m", "dev", null, null, "TOKEN_INVALID", null);

    assertThat(response.selectionType()).isEqualTo("errorCode");
    assertThat(response.recentSamples()).hasSize(1);
    assertThat(response.recentSamples().get(0).traceUrl()).isEqualTo("https://logs.example.test/trace/trace-join-1");
  }

    @Test
    void rejectsUnknownEnvironmentWithInvalidRequestProblemSignal() {
        assertThatThrownBy(() -> service.getSummary("tenant-1", "15m", "qa", null, null, "trace-dashboard-3"))
                .isInstanceOf(AdminDashboardInvalidRequestException.class)
                .hasMessageContaining("environment");
    }
}