package com.acme.jitsi.domains.admin.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.acme.jitsi.domains.admin.dto.AdminDashboardDrillDownResponse;
import com.acme.jitsi.domains.admin.dto.AdminDashboardSummaryResponse;
import com.acme.jitsi.domains.admin.service.AdminDashboardInvalidRequestException;
import com.acme.jitsi.domains.admin.service.AdminDashboardService;
import com.acme.jitsi.security.ProblemDetailsMappingPolicy;
import com.acme.jitsi.security.ProblemResponseFacade;
import com.acme.jitsi.security.TenantAccessGuard;
import com.acme.jitsi.shared.ErrorCode;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = AdminDashboardController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(AdminDashboardExceptionHandler.class)
@Tag("slice")
class AdminDashboardControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private AdminDashboardService adminDashboardService;

  @MockitoBean
  private ProblemResponseFacade problemResponseFacade;

  @MockitoBean
  private TenantAccessGuard tenantAccessGuard;

  @MockitoBean
  private ProblemDetailsMappingPolicy problemDetailsMappingPolicy;

  @Test
  void summaryEndpointReturnsTypedDashboardPayload() throws Exception {
    when(tenantAccessGuard.resolveTenantId(any())).thenReturn("tenant-1");
    when(problemResponseFacade.resolveTraceId(any())).thenReturn("trace-admin-1");
    when(adminDashboardService.getSummary(
        eq("tenant-1"), eq("1h"), eq("dev"), eq("room-1"), eq("meeting-1"), eq("trace-admin-1")))
            .thenReturn(new AdminDashboardSummaryResponse(
                "1h",
                "dev",
                "tenant-1",
                "2026-03-18T10:00:00Z",
                "trace-admin-1",
                new AdminDashboardSummaryResponse.PriorityBanner(
                    true,
                    "critical",
                    "Config mismatch blocks joins",
                    "Config incompatibility is the dominant signal.",
                    "Открыть очередь инцидентов",
                    new AdminDashboardSummaryResponse.HandoffContext(
                        "dev",
                        "1h",
                        "critical",
                        "CONFIG_INCOMPATIBLE",
                        "CONFIG",
                        "room-1",
                        "meeting-1",
                        null)),
                List.of(new AdminDashboardSummaryResponse.DegradationSummary(
                    "config-compatibility",
                    "Config compatibility requires immediate attention",
                    "Role mismatch blocks join attempts.",
                    "critical",
                    "Открыть очередь инцидентов",
                    new AdminDashboardSummaryResponse.HandoffContext(
                        "dev",
                        "1h",
                        "critical",
                        "CONFIG_INCOMPATIBLE",
                        "CONFIG",
                        "room-1",
                        "meeting-1",
                        null))),
                List.of(new AdminDashboardSummaryResponse.ServiceStatus(
                    "backend-api",
                    "Backend API",
                    "DOWN",
                    "Health surface reports a degraded dependency.",
                    new AdminDashboardSummaryResponse.HandoffContext(
                        "dev",
                        "1h",
                        "critical",
                        null,
                        "CONFIG",
                        "room-1",
                        "meeting-1",
                        null))),
                List.of(new AdminDashboardSummaryResponse.LatestSpike(
                    "CONFIG_INCOMPATIBLE",
                    "CONFIG",
                    2,
                    "Two recent failures share the same config incompatibility code.",
                    new AdminDashboardSummaryResponse.HandoffContext(
                        "dev",
                        "1h",
                        "critical",
                        "CONFIG_INCOMPATIBLE",
                        "CONFIG",
                        "room-1",
                        "meeting-1",
                        null))),
                List.of(new AdminDashboardSummaryResponse.AffectedScopeSummary(
                    "room",
                    "room-1",
                    2,
                    "room-1 is the dominant affected scope for the selected window.",
                    new AdminDashboardSummaryResponse.HandoffContext(
                        "dev",
                        "1h",
                        "critical",
                        "CONFIG_INCOMPATIBLE",
                        "CONFIG",
                        "room-1",
                        null,
                        null))),
                new AdminDashboardSummaryResponse.SafeStateSummary(
                    false,
                    "Есть активные сигналы",
                    "Use the incident queue handoff for investigation.",
                    List.of(new AdminDashboardSummaryResponse.SafeStateAction(
                        "Открыть очередь инцидентов",
                        "/admin/incidents?period=1h&environment=dev")),
                    List.of()),
                new AdminDashboardSummaryResponse.EntityFilter("room-1", "meeting-1"),
                false));

    mockMvc.perform(get("/api/v1/admin/dashboard")
            .param("period", "1h")
            .param("environment", "dev")
            .param("roomId", "room-1")
            .param("meetingId", "meeting-1")
            .with(oauth2Login()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.period").value("1h"))
        .andExpect(jsonPath("$.environment").value("dev"))
          .andExpect(jsonPath("$.priorityBanner.headline").value("Config mismatch blocks joins"))
          .andExpect(jsonPath("$.topDegradations[0].id").value("config-compatibility"))
          .andExpect(jsonPath("$.latestSpikes[0].errorCode").value("CONFIG_INCOMPATIBLE"))
          .andExpect(jsonPath("$.affectedScopeSummary[0].scopeValue").value("room-1"))
        .andExpect(jsonPath("$.entityFilter.roomId").value("room-1"));
  }

  @Test
  void drillDownEndpointReturnsRecentSamples() throws Exception {
    when(tenantAccessGuard.resolveTenantId(any())).thenReturn("tenant-1");
    when(adminDashboardService.getDrillDown(
        eq("tenant-1"), eq("15m"), eq("dev"), eq("room-1"), eq("meeting-1"), eq("TOKEN_INVALID"), eq(null)))
            .thenReturn(new AdminDashboardDrillDownResponse(
                "15m",
                "dev",
                "tenant-1",
                "2026-03-18T10:00:00Z",
                "errorCode",
                "TOKEN_INVALID",
                new AdminDashboardDrillDownResponse.EntityFilter("room-1", "meeting-1"),
                2,
                List.of(new AdminDashboardDrillDownResponse.RecentSample(
                    "2026-03-18T09:58:00Z",
                    "room-1",
                    "meeting-1",
                    "subject-1",
                    "trace-1",
                    "https://ops.example.test/trace-1",
                    "TOKEN_INVALID",
                    "TOKEN",
                    "Повторите вход через SSO")),
                false));

    mockMvc.perform(get("/api/v1/admin/dashboard/drill-down")
            .param("period", "15m")
            .param("environment", "dev")
            .param("roomId", "room-1")
            .param("meetingId", "meeting-1")
            .param("errorCode", "TOKEN_INVALID")
            .with(oauth2Login()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.selectionType").value("errorCode"))
        .andExpect(jsonPath("$.selectionValue").value("TOKEN_INVALID"))
        .andExpect(jsonPath("$.recentSamples[0].traceId").value("trace-1"));
  }

    @Test
    void summaryEndpointReturnsProblemDetailForInvalidEnvironment() throws Exception {
        when(tenantAccessGuard.resolveTenantId(any())).thenReturn("tenant-1");
        when(problemResponseFacade.resolveTraceId(any())).thenReturn("trace-admin-2");
        when(problemResponseFacade.buildProblemDetail(any(), eq(HttpStatus.BAD_REQUEST), any(), any(), eq(ErrorCode.INVALID_REQUEST.code())))
                .thenAnswer(invocation -> {
                    ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, invocation.getArgument(3, String.class));
                    detail.setTitle(invocation.getArgument(2, String.class));
                    detail.setProperty("errorCode", invocation.getArgument(4, String.class));
                    detail.setProperty("traceId", "trace-admin-2");
                    return detail;
                });
        when(adminDashboardService.getSummary(
                eq("tenant-1"), eq("15m"), eq("qa"), eq(null), eq(null), eq("trace-admin-2")))
                        .thenThrow(new AdminDashboardInvalidRequestException("Параметр environment должен быть одним из: [DEV, TEST, PROD]."));

        mockMvc.perform(get("/api/v1/admin/dashboard")
                        .param("environment", "qa")
                        .with(oauth2Login()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Некорректный запрос operational dashboard"))
                .andExpect(jsonPath("$.detail").value("Параметр environment должен быть одним из: [DEV, TEST, PROD]."))
                .andExpect(jsonPath("$.errorCode").value(ErrorCode.INVALID_REQUEST.code()))
                .andExpect(jsonPath("$.traceId").value("trace-admin-2"));
    }
}