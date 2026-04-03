package com.acme.jitsi.domains.admin.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.acme.jitsi.domains.admin.dto.AdminIncidentDetailResponse;
import com.acme.jitsi.domains.admin.dto.AdminIncidentListResponse;
import com.acme.jitsi.domains.admin.dto.AdminIncidentSearchResponse;
import com.acme.jitsi.domains.admin.dto.AdminIncidentTicketResponse;
import com.acme.jitsi.domains.admin.service.AdminIncidentsInvalidRequestException;
import com.acme.jitsi.domains.admin.service.AdminIncidentsService;
import com.acme.jitsi.security.ProblemDetailsMappingPolicy;
import com.acme.jitsi.security.ProblemResponseFacade;
import com.acme.jitsi.security.TenantAccessGuard;
import com.acme.jitsi.shared.ErrorCode;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = AdminIncidentsController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(AdminIncidentsExceptionHandler.class)
@Tag("slice")
class AdminIncidentsControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private AdminIncidentsService adminIncidentsService;

  @MockitoBean
  private ProblemResponseFacade problemResponseFacade;

  @MockitoBean
  private TenantAccessGuard tenantAccessGuard;

  @MockitoBean
  private ProblemDetailsMappingPolicy problemDetailsMappingPolicy;

  @Test
  void listEndpointReturnsTypedIncidentPayload() throws Exception {
    when(tenantAccessGuard.resolveTenantId(any())).thenReturn("tenant-1");
    when(adminIncidentsService.listIncidents(any(), any(), any()))
        .thenReturn(new AdminIncidentListResponse(
            "1h",
            "dev",
            "tenant-1",
            "2026-03-18T10:00:00Z",
            "active",
            "scope:room",
            List.of(
                new AdminIncidentListResponse.SavedView("active", "Active", "Открытые инциденты для triage"),
                new AdminIncidentListResponse.SavedView("critical", "Critical", "Критические сигналы выше остальных")),
            List.of(
                new AdminIncidentListResponse.QuickFacet("scope:room", "Комнаты", 3, true),
                new AdminIncidentListResponse.QuickFacet("severity:critical", "Critical", 1, false)),
            new AdminIncidentListResponse.QueueSort("queue", "Severity + freshness", "desc"),
            50,
            0,
            2,
            List.of(
                new AdminIncidentListResponse.IncidentListItem(
                    "incident-1",
                    "2026-03-18T09:58:00Z",
                    "TOKEN_INVALID",
                    "TOKEN",
                    "tenant-1",
                    "room-1",
                    "meeting-1",
                    2,
                    "warn",
                    "Комната room-1, встреча meeting-1, 2 затронутых субъекта",
                    "Активность 2 минуты назад"),
                new AdminIncidentListResponse.IncidentListItem(
                    "incident-2",
                    "2026-03-18T09:12:00Z",
                    "CONFIG_INCOMPATIBLE",
                    "CONFIG",
                    "tenant-1",
                    null,
                    null,
                    10,
                    "critical",
                    "10 затронутых субъектов без привязки к комнате",
                    "Последний всплеск 48 минут назад"))));

    mockMvc.perform(get("/api/v1/admin/incidents")
            .param("period", "1h")
            .param("environment", "dev")
            .with(oauth2Login()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.selectedView").value("active"))
        .andExpect(jsonPath("$.selectedQuickFacet").value("scope:room"))
        .andExpect(jsonPath("$.availableViews[0].token").value("active"))
        .andExpect(jsonPath("$.quickFacets[0].active").value(true))
        .andExpect(jsonPath("$.sort.token").value("queue"))
        .andExpect(jsonPath("$.items[0].incidentId").value("incident-1"))
        .andExpect(jsonPath("$.items[0].affectedEntitySummary").value("Комната room-1, встреча meeting-1, 2 затронутых субъекта"))
        .andExpect(jsonPath("$.items[1].severity").value("critical"))
        .andExpect(jsonPath("$.pageSize").value(50));
  }

  @Test
  void detailEndpointReturnsAffectedAttemptsAndTicketingState() throws Exception {
    when(tenantAccessGuard.resolveTenantId(any())).thenReturn("tenant-1");
    when(adminIncidentsService.getIncidentDetail(any(), any(), any(), any()))
        .thenReturn(new AdminIncidentDetailResponse(
            "incident-1",
            "tenant-1",
            "dev",
            "TOKEN_INVALID",
            "TOKEN",
            "warn",
            "Сбой входа по токену",
            "2026-03-18T09:45:00Z",
            "2026-03-18T10:00:00Z",
            List.of(new AdminIncidentDetailResponse.AffectedAttempt(
                "2026-03-18T09:58:00Z",
                "trace-1",
                "trace-1",
                "subject-1",
                "subject-1",
                "participant",
                "diagnostic",
                "room-1",
                "meeting-1",
                "https://tempo.example.test/traces/trace-1")),
            new AdminIncidentDetailResponse.SummaryBar(
                "TOKEN_INVALID incident",
                "TOKEN_INVALID / TOKEN",
                "Комната room-1, встреча meeting-1, 1 затронутый субъект",
                "active-investigation",
                "2026-03-18T09:45:00Z → 2026-03-18T10:00:00Z",
                "dev"),
            List.of(new AdminIncidentDetailResponse.TimelineEntry(
                "2026-03-18T09:58:00Z",
                "Повторный отказ входа",
                "participant · subject-1",
                "subject-1",
                "participant",
                "trace-1",
                "trace-1",
                "room-1",
                "meeting-1")),
            List.of(new AdminIncidentDetailResponse.EvidenceBlock(
                "diagnostics",
                "Diagnostics result",
                "available",
                "diagnostic",
                "participant signal retained for investigation",
                "trace-1",
                "trace-1",
                "https://tempo.example.test/traces/trace-1",
                null)),
            List.of(new AdminIncidentDetailResponse.RelatedLink(
                "role-history",
                "История ролей по субъекту",
                "dev",
                "subject-1",
                "room-1",
                "meeting-1",
                "trace-1",
                null)),
            List.of(new AdminIncidentDetailResponse.NextAction(
                "queue",
                "Вернуться в очередь",
                "Сохранить incident context и продолжить triage",
                "queue-return",
                null)),
            new AdminIncidentDetailResponse.CoordinationState(
                true,
                "available",
                "Coordination seam remains optional and investigation-first.",
                "lead.support",
                "investigating",
                "INC-42",
                "linked",
                "https://tickets.example.test/INC-42",
                List.of(new AdminIncidentDetailResponse.CoordinationAuditEntry(
                    "2026-03-18T09:59:00Z",
                    "admin-user",
                    "coordination-updated",
                    "trace-1",
                    "owner=<none>; workflowStatus=triage; ticketReference=<none>; ticketStatus=not-linked",
                    "owner=lead.support; workflowStatus=investigating; ticketReference=INC-42; ticketStatus=linked"))),
            new AdminIncidentDetailResponse.TicketingState(false, null, null, "disabled")));

    mockMvc.perform(get("/api/v1/admin/incidents/incident-1")
            .param("environment", "dev")
            .with(oauth2Login()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.summaryBar.title").value("TOKEN_INVALID incident"))
        .andExpect(jsonPath("$.summaryBar.operationalStatus").value("active-investigation"))
        .andExpect(jsonPath("$.timeline[0].title").value("Повторный отказ входа"))
        .andExpect(jsonPath("$.evidence[0].kind").value("diagnostics"))
        .andExpect(jsonPath("$.relatedLinks[0].kind").value("role-history"))
        .andExpect(jsonPath("$.nextActions[0].target").value("queue-return"))
        .andExpect(jsonPath("$.affectedAttempts[0].traceId").value("trace-1"))
                .andExpect(jsonPath("$.coordination.enabled").value(true))
                .andExpect(jsonPath("$.coordination.owner").value("lead.support"))
                .andExpect(jsonPath("$.coordination.workflowStatus").value("investigating"))
                .andExpect(jsonPath("$.coordination.ticketReference").value("INC-42"))
                .andExpect(jsonPath("$.coordination.history[0].actionType").value("coordination-updated"))
        .andExpect(jsonPath("$.ticketing.status").value("disabled"));
  }

    @Test
    void coordinationEndpointReturnsUpdatedLightweightSeamState() throws Exception {
        when(tenantAccessGuard.resolveTenantId(any())).thenReturn("tenant-1");
        when(adminIncidentsService.updateCoordination(any(), any(), anyString(), anyString(), any(), anyString()))
                .thenReturn(new AdminIncidentDetailResponse.CoordinationState(
                        true,
                        "available",
                        "Coordination seam remains lightweight and optional.",
                        "lead.support",
                        "investigating",
                        null,
                        "not-linked",
                        null,
                        List.of(new AdminIncidentDetailResponse.CoordinationAuditEntry(
                                "2026-03-18T10:02:00Z",
                                "admin-user",
                                "coordination-updated",
                                "trace-admin-incident",
                                "owner=<none>; workflowStatus=triage; ticketReference=<none>; ticketStatus=not-linked",
                                "owner=lead.support; workflowStatus=investigating; ticketReference=<none>; ticketStatus=not-linked"))));

        mockMvc.perform(post("/api/v1/admin/incidents/incident-1/coordination")
                        .param("environment", "dev")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "owner": "lead.support",
                                    "workflowStatus": "investigating"
                                }
                                """)
                        .with(oauth2Login()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.owner").value("lead.support"))
                .andExpect(jsonPath("$.workflowStatus").value("investigating"))
                .andExpect(jsonPath("$.ticketStatus").value("not-linked"))
                .andExpect(jsonPath("$.history[0].actorId").value("admin-user"));
    }

  @Test
  void searchEndpointReturnsCandidateListOutcome() throws Exception {
    when(tenantAccessGuard.resolveTenantId(any())).thenReturn("tenant-1");
    when(adminIncidentsService.searchIncidents(any(), any(), any()))
        .thenReturn(new AdminIncidentSearchResponse(
            "candidate-list",
            null,
            null,
            "Уточните tenant или entity filters.",
            List.of(
                new AdminIncidentSearchResponse.SearchCandidate("incident-1", "2026-03-18T09:58:00Z", "TOKEN_INVALID", "meeting-1"),
                new AdminIncidentSearchResponse.SearchCandidate("incident-2", "2026-03-18T09:54:00Z", "TOKEN_INVALID", "meeting-2"))));

    mockMvc.perform(get("/api/v1/admin/incidents/search")
            .param("environment", "dev")
            .param("errorCode", "TOKEN_INVALID")
            .param("from", "2026-03-18T09:45:00Z")
            .param("to", "2026-03-18T10:00:00Z")
            .with(oauth2Login()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.outcome").value("candidate-list"))
        .andExpect(jsonPath("$.candidates[0].incidentId").value("incident-1"));
  }

    @Test
    void searchEndpointReturnsProblemDetailForInvalidTimestamp() throws Exception {
        when(tenantAccessGuard.resolveTenantId(any())).thenReturn("tenant-1");
        when(problemResponseFacade.resolveTraceId(any())).thenReturn("trace-admin-incident");
        when(problemResponseFacade.buildProblemDetail(any(), eq(HttpStatus.BAD_REQUEST), anyString(), anyString(), eq(ErrorCode.INVALID_REQUEST.code())))
                .thenAnswer(invocation -> {
                    ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, invocation.getArgument(3, String.class));
                    detail.setTitle(invocation.getArgument(2, String.class));
                    detail.setProperty("errorCode", invocation.getArgument(4, String.class));
                    detail.setProperty("traceId", "trace-admin-incident");
                    return detail;
                });
        when(adminIncidentsService.searchIncidents(any(), any(), any()))
                .thenThrow(new AdminIncidentsInvalidRequestException("Параметр from должен быть ISO-8601 timestamp."));

        mockMvc.perform(get("/api/v1/admin/incidents/search")
                        .param("environment", "dev")
                        .param("from", "broken")
                        .with(oauth2Login()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Некорректный запрос incident cabinet"))
                .andExpect(jsonPath("$.detail").value("Параметр from должен быть ISO-8601 timestamp."))
                .andExpect(jsonPath("$.errorCode").value(ErrorCode.INVALID_REQUEST.code()))
                .andExpect(jsonPath("$.traceId").value("trace-admin-incident"));
    }

  @Test
  void ticketEndpointReturnsCreatedTicketPayload() throws Exception {
    when(tenantAccessGuard.resolveTenantId(any())).thenReturn("tenant-1");
        when(adminIncidentsService.createTicket(any(), any(), any(), any(), anyString()))
        .thenReturn(new AdminIncidentTicketResponse(true, true, "INC-42", "https://tickets.example.test/INC-42", "TOKEN_INVALID incident", null));

    mockMvc.perform(post("/api/v1/admin/incidents/incident-1/ticket")
            .param("environment", "dev")
            .with(oauth2Login()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.ticketKey").value("INC-42"))
        .andExpect(jsonPath("$.ticketUrl").value("https://tickets.example.test/INC-42"));
  }
}