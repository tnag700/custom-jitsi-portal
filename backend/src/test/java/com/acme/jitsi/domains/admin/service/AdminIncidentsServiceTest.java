package com.acme.jitsi.domains.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.acme.jitsi.domains.admin.dto.AdminIncidentCoordinationUpdateRequest;
import com.acme.jitsi.domains.admin.dto.AdminIncidentDetailResponse;
import com.acme.jitsi.domains.admin.dto.AdminIncidentListResponse;
import com.acme.jitsi.domains.admin.dto.AdminIncidentSearchResponse;
import com.acme.jitsi.domains.admin.dto.AdminIncidentTicketResponse;
import com.acme.jitsi.domains.configsets.service.ConfigSetEnvironmentType;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminIncidentsServiceTest {

  @Mock
  private AdminIncidentsReadModel readModel;

  @Mock
  private AdminIncidentTicketPort ticketPort;

    @Mock
    private AdminIncidentCoordinationPort coordinationPort;

  private AdminIncidentsService service;

  @BeforeEach
  void setUp() {
        lenient().when(coordinationPort.describe(any())).thenReturn(new AdminIncidentCoordinationPort.CoordinationSnapshot(
        false,
        "disabled",
        "Coordination seam remains optional.",
        null,
        "not-enabled",
        null,
        "not-linked",
        null,
        List.of()));
    service = new AdminIncidentsService(
        readModel,
        ticketPort,
        coordinationPort,
        Clock.fixed(Instant.parse("2026-03-18T10:00:00Z"), ZoneOffset.UTC),
        "https://tempo.example.test/traces/{traceId}",
        "PT168H");
  }

  @Test
    void aggregatesSignalsIntoPagedIncidentListWithQueueMetadataAndOpinionatedSorting() {
    when(readModel.loadSignals(any())).thenReturn(List.of(
        signal("2026-03-18T09:58:00Z", "tenant-1", ConfigSetEnvironmentType.DEV, "room-1", "meeting-1", "subject-1", "trace-1", "TOKEN_INVALID", "TOKEN", null, null),
        signal("2026-03-18T09:54:00Z", "tenant-1", ConfigSetEnvironmentType.DEV, "room-1", "meeting-1", "subject-2", "trace-2", "TOKEN_INVALID", "TOKEN", null, null),
        signal("2026-03-18T09:12:00Z", "tenant-1", ConfigSetEnvironmentType.DEV, "room-2", "meeting-2", "subject-3", "trace-3", "CONFIG_INCOMPATIBLE", "CONFIG", "critical", "blocked")));

    AdminIncidentListResponse response = service.listIncidents(
        "tenant-1",
        List.of("ROLE_admin"),
        new AdminIncidentsService.AdminIncidentListQuery("1h", "dev", null, null, null, null, null, null, null, null, 50, 0, "queue", "desc"));

    assertThat(response.items()).hasSize(2);
    assertThat(response.items().get(0).incidentId()).isNotBlank();
    assertThat(response.selectedView()).isEqualTo("active");
    assertThat(response.availableViews()).extracting(AdminIncidentListResponse.SavedView::token)
        .containsExactly("active", "critical", "new-spikes", "needs-refinement");
    assertThat(response.quickFacets()).isNotEmpty();
    assertThat(response.sort()).extracting(AdminIncidentListResponse.QueueSort::token, AdminIncidentListResponse.QueueSort::direction)
        .containsExactly("queue", "desc");
    assertThat(response.items().get(0).severity()).isEqualTo("critical");
    assertThat(response.items().get(0).affectedEntitySummary()).contains("meeting-2");
    assertThat(response.items().get(0).freshnessHint()).isNotBlank();
    assertThat(response.items().get(1).affectedSubjects()).isEqualTo(2);
    assertThat(response.pageSize()).isEqualTo(50);
    assertThat(response.totalElements()).isEqualTo(2);
  }

  @Test
  void detailMasksSubjectForSupportEngineerAndKeepsTraceIdCopyFallback() {
        when(ticketPort.describeTicketing(any())).thenReturn(new AdminIncidentTicketPort.TicketingStatus(false, null, null, "disabled"));
    when(readModel.loadSignals(any())).thenReturn(List.of(
        signal("2026-03-18T09:58:00Z", "tenant-1", ConfigSetEnvironmentType.DEV, "room-1", "meeting-1", "subject-sensitive-123", "trace-1", "TOKEN_INVALID", "TOKEN", null, null),
        signal("2026-03-18T09:54:00Z", "tenant-1", ConfigSetEnvironmentType.DEV, "room-1", "meeting-1", "subject-sensitive-456", "trace-2", "TOKEN_INVALID", "TOKEN", null, null)));

    String incidentId = service.listIncidents(
        "tenant-1",
        List.of("ROLE_admin"),
        new AdminIncidentsService.AdminIncidentListQuery("1h", "dev", null, null, null, null, null, null, null, null, 50, 0, "time", "desc"))
        .items()
        .get(0)
        .incidentId();

    AdminIncidentDetailResponse response = service.getIncidentDetail(
        "tenant-1",
        List.of("ROLE_support-engineer"),
        incidentId,
        "dev");

    assertThat(response.affectedAttempts()).hasSize(2);
    assertThat(response.affectedAttempts().get(0).subjectDisplay()).contains("***");
    assertThat(response.affectedAttempts().get(0).subjectIdFilterValue()).isNull();
    assertThat(response.affectedAttempts().get(0).traceId()).isEqualTo("trace-1");
    assertThat(response.affectedAttempts().get(0).traceUrl()).isEqualTo("https://tempo.example.test/traces/trace-1");
    assertThat(response.summaryBar().title()).contains("TOKEN_INVALID");
    assertThat(response.summaryBar().operationalStatus()).isEqualTo("active-investigation");
    assertThat(response.timeline()).hasSize(2);
    assertThat(response.timeline().get(0).traceId()).isEqualTo("trace-1");
    assertThat(response.evidence()).isNotEmpty();
    assertThat(response.relatedLinks()).extracting(AdminIncidentDetailResponse.RelatedLink::kind)
        .contains("role-history");
    assertThat(response.nextActions()).extracting(AdminIncidentDetailResponse.NextAction::target)
        .contains("queue-return");
  }

  @Test
  void detailBuildsActionableEmptyStatesWhenOptionalEvidenceIsMissing() {
    when(ticketPort.describeTicketing(any())).thenReturn(new AdminIncidentTicketPort.TicketingStatus(false, null, null, "disabled"));
    when(readModel.loadSignals(any())).thenReturn(List.of(
        signalWithoutOptionalEvidence("2026-03-18T09:58:00Z", "tenant-1", ConfigSetEnvironmentType.DEV, null, null, "subject-1", null, "TOKEN_INVALID", "TOKEN", null, null)));

    String incidentId = service.listIncidents(
        "tenant-1",
        List.of("ROLE_admin"),
        new AdminIncidentsService.AdminIncidentListQuery("1h", "dev", null, null, null, null, null, null, null, null, 50, 0, "time", "desc"))
        .items()
        .get(0)
        .incidentId();

    AdminIncidentDetailResponse response = service.getIncidentDetail(
        "tenant-1",
        List.of("ROLE_admin"),
        incidentId,
        "dev");

    assertThat(response.evidence()).isNotEmpty();
    assertThat(response.evidence().get(0).status()).isEqualTo("empty");
    assertThat(response.evidence().get(0).emptyState()).isNotNull();
    assertThat(response.evidence().get(0).emptyState().title()).isEqualTo("Нет diagnostics result");
    assertThat(response.relatedLinks()).isEmpty();
    assertThat(response.nextActions()).extracting(AdminIncidentDetailResponse.NextAction::target)
        .contains("queue-return");
    assertThat(response.evidence().get(1).emptyState()).isNotNull();
    assertThat(response.evidence().get(1).emptyState().title()).isEqualTo("Нет trace link");
  }

  @Test
  void detailFallsBackToOlderAttemptWhenLatestAttemptHasNoEvidence() {
    when(ticketPort.describeTicketing(any())).thenReturn(new AdminIncidentTicketPort.TicketingStatus(false, null, null, "disabled"));
    when(readModel.loadSignals(any())).thenReturn(List.of(
        signalWithoutOptionalEvidence("2026-03-18T09:58:00Z", "tenant-1", ConfigSetEnvironmentType.DEV, "room-1", "meeting-1", "subject-1", null, "TOKEN_INVALID", "TOKEN", null, null),
        signal("2026-03-18T09:54:00Z", "tenant-1", ConfigSetEnvironmentType.DEV, "room-1", "meeting-1", "subject-1", "trace-older", "TOKEN_INVALID", "TOKEN", null, null)));

    String incidentId = service.listIncidents(
        "tenant-1",
        List.of("ROLE_admin"),
        new AdminIncidentsService.AdminIncidentListQuery("1h", "dev", null, null, null, null, null, null, null, null, 50, 0, "time", "desc"))
        .items()
        .get(0)
        .incidentId();

    AdminIncidentDetailResponse response = service.getIncidentDetail(
        "tenant-1",
        List.of("ROLE_admin"),
        incidentId,
        "dev");

    assertThat(response.evidence().get(0).status()).isEqualTo("available");
    assertThat(response.evidence().get(0).summary()).isEqualTo("diagnostic");
    assertThat(response.evidence().get(1).status()).isEqualTo("available");
    assertThat(response.relatedLinks()).extracting(AdminIncidentDetailResponse.RelatedLink::kind)
        .contains("role-history", "incident-scope", "trace");
  }

  @Test
  void searchSupportsExactMatchAndCandidateListOutcomes() {
    when(readModel.loadSignals(any()))
        .thenReturn(List.of(
            signal("2026-03-18T09:58:00Z", "tenant-1", ConfigSetEnvironmentType.DEV, "room-exact", "meeting-exact", "subject-1", "trace-exact", "TOKEN_INVALID", "TOKEN", null, null)))
        .thenReturn(List.of(
            signal("2026-03-18T09:54:00Z", "tenant-1", ConfigSetEnvironmentType.DEV, "room-1", "meeting-1", "subject-2", "trace-candidate-1", "TOKEN_INVALID", "TOKEN", null, null),
            signal("2026-03-18T09:50:00Z", "tenant-1", ConfigSetEnvironmentType.DEV, "room-2", "meeting-1", "subject-3", "trace-candidate-2", "TOKEN_INVALID", "TOKEN", null, null),
            signal("2026-03-18T09:58:00Z", "tenant-1", ConfigSetEnvironmentType.DEV, "room-3", "meeting-2", "subject-4", "trace-candidate-3", "TOKEN_INVALID", "TOKEN", null, null)));

    AdminIncidentSearchResponse exact = service.searchIncidents(
        "tenant-1",
        List.of("ROLE_admin"),
        new AdminIncidentsService.AdminIncidentSearchQuery("dev", "trace-exact", null, null, null, null, null));

    AdminIncidentSearchResponse candidates = service.searchIncidents(
        "tenant-1",
        List.of("ROLE_admin"),
        new AdminIncidentsService.AdminIncidentSearchQuery("dev", null, null, "TOKEN_INVALID", "2026-03-18T09:53:00Z", "2026-03-18T09:55:00Z", "meeting-1"));

    assertThat(exact.outcome()).isEqualTo("exact-match");
    assertThat(exact.incidentId()).isNotBlank();
    assertThat(candidates.outcome()).isEqualTo("candidate-list");
    assertThat(candidates.candidates()).hasSize(3);
    assertThat(candidates.candidates())
        .extracting(AdminIncidentSearchResponse.SearchCandidate::meetingId)
        .containsExactly("meeting-1", "meeting-1", "meeting-2");
  }

    @Test
    void searchRejectsInvalidIsoTimestampsWithBoundedRequestError() {
        assertThatThrownBy(() -> service.searchIncidents(
                "tenant-1",
                List.of("ROLE_admin"),
                new AdminIncidentsService.AdminIncidentSearchQuery("dev", null, null, "TOKEN_INVALID", "not-a-timestamp", null, null)))
                .isInstanceOf(AdminIncidentsInvalidRequestException.class)
                .hasMessage("Параметр from должен быть ISO-8601 timestamp.");
    }

  @Test
  void detailWithoutExplicitEnvironmentUsesIncidentEnvironmentInsteadOfDefaultingToDev() {
    when(ticketPort.describeTicketing(any())).thenReturn(new AdminIncidentTicketPort.TicketingStatus(false, null, null, "disabled"));
    when(readModel.loadSignals(any())).thenReturn(List.of(
        signal("2026-03-18T09:58:00Z", "tenant-1", ConfigSetEnvironmentType.PROD, "room-1", "meeting-1", "subject-1", "trace-prod-1", "TOKEN_INVALID", "TOKEN", null, null)));

    String incidentId = service.listIncidents(
        "tenant-1",
        List.of("ROLE_admin"),
        new AdminIncidentsService.AdminIncidentListQuery("1h", null, null, null, null, null, null, null, null, null, 50, 0, "time", "desc"))
        .items()
        .get(0)
        .incidentId();

    AdminIncidentDetailResponse response = service.getIncidentDetail(
        "tenant-1",
        List.of("ROLE_admin"),
        incidentId,
        null);

    assertThat(response.environment()).isEqualTo("prod");
  }

    @Test
    void detailLookupUsesExpandedSampleWindowInsideRetentionPeriod() {
        when(ticketPort.describeTicketing(any())).thenReturn(new AdminIncidentTicketPort.TicketingStatus(false, null, null, "disabled"));
        AdminIncidentsReadModel.IncidentSignal targetSignal = signal(
                "2026-03-18T09:58:00Z",
                "tenant-1",
                ConfigSetEnvironmentType.DEV,
                "room-target",
                "meeting-target",
                "subject-target",
                "trace-target",
                "TOKEN_INVALID",
                "TOKEN",
                null,
                null);
        AdminIncidentsReadModel.IncidentSignal fallbackSignal = signal(
                "2026-03-18T09:57:00Z",
                "tenant-1",
                ConfigSetEnvironmentType.DEV,
                "room-other",
                "meeting-other",
                "subject-other",
                "trace-other",
                "TOKEN_INVALID",
                "TOKEN",
                null,
                null);
        when(readModel.loadSignals(any())).thenAnswer(invocation -> {
            AdminIncidentsReadModel.SignalFilter filter = invocation.getArgument(0);
            return filter.sampleLimit() < 600 ? List.of(fallbackSignal) : List.of(targetSignal, fallbackSignal);
        });

        String incidentId = service.listIncidents(
                "tenant-1",
                List.of("ROLE_admin"),
                new AdminIncidentsService.AdminIncidentListQuery("1h", "dev", null, null, null, null, null, null, null, null, 50, 0, "time", "desc"))
                .items()
                .get(0)
                .incidentId();

        AdminIncidentDetailResponse response = service.getIncidentDetail(
                "tenant-1",
                List.of("ROLE_admin"),
                incidentId,
                "dev");

        assertThat(response.incidentId()).isEqualTo(incidentId);
        assertThat(response.summaryBar().affectedScope()).contains("room-target");
    }

  @Test
  void createTicketUsesServerSidePortAndReturnsCreatedLink() {
    when(readModel.loadSignals(any())).thenReturn(List.of(
        signal("2026-03-18T09:58:00Z", "tenant-1", ConfigSetEnvironmentType.DEV, "room-1", "meeting-1", "subject-1", "trace-1", "TOKEN_INVALID", "TOKEN", null, null)));
    when(ticketPort.createTicket(any())).thenReturn(new AdminIncidentTicketPort.TicketCreationResult(
        true,
        true,
        "INC-42",
        "https://tickets.example.test/INC-42",
        "TOKEN_INVALID incident for tenant-1",
        null));

    String incidentId = service.listIncidents(
        "tenant-1",
        List.of("ROLE_admin"),
        new AdminIncidentsService.AdminIncidentListQuery("1h", "dev", null, null, null, null, null, null, null, null, 50, 0, "time", "desc"))
        .items()
        .get(0)
        .incidentId();

    AdminIncidentTicketResponse response = service.createTicket(
        "tenant-1",
        List.of("ROLE_admin"),
        incidentId,
        "dev",
        "admin-user");

    assertThat(response.available()).isTrue();
    assertThat(response.created()).isTrue();
    assertThat(response.ticketKey()).isEqualTo("INC-42");
  }

  @Test
  void updateCoordinationForwardsExistingTicketReferenceAndStatus() {
    when(readModel.loadSignals(any())).thenReturn(List.of(
        signal("2026-03-18T09:58:00Z", "tenant-1", ConfigSetEnvironmentType.DEV, "room-1", "meeting-1", "subject-1", "trace-1", "TOKEN_INVALID", "TOKEN", null, null)));
    when(coordinationPort.update(any())).thenReturn(new AdminIncidentCoordinationPort.CoordinationSnapshot(
        true,
        "available",
        "Coordination seam remains optional.",
        "lead.support",
        "waiting-external",
        "INC-42",
        "waiting-external",
        null,
        List.of()));

    String incidentId = service.listIncidents(
        "tenant-1",
        List.of("ROLE_admin"),
        new AdminIncidentsService.AdminIncidentListQuery("1h", "dev", null, null, null, null, null, null, null, null, 50, 0, "time", "desc"))
        .items()
        .get(0)
        .incidentId();

    AdminIncidentDetailResponse.CoordinationState response = service.updateCoordination(
        "tenant-1",
        List.of("ROLE_admin"),
        incidentId,
        "dev",
        new AdminIncidentCoordinationUpdateRequest("lead.support", "investigating", "INC-42", "waiting-external"),
        "admin-user");

    ArgumentCaptor<AdminIncidentCoordinationPort.CoordinationUpdateCommand> commandCaptor =
        ArgumentCaptor.forClass(AdminIncidentCoordinationPort.CoordinationUpdateCommand.class);
    verify(coordinationPort).update(commandCaptor.capture());

    assertThat(commandCaptor.getValue().owner()).isEqualTo("lead.support");
    assertThat(commandCaptor.getValue().workflowStatus()).isEqualTo("investigating");
    assertThat(commandCaptor.getValue().ticketReference()).isEqualTo("INC-42");
    assertThat(commandCaptor.getValue().ticketStatus()).isEqualTo("waiting-external");
    assertThat(response.ticketReference()).isEqualTo("INC-42");
    assertThat(response.ticketStatus()).isEqualTo("waiting-external");
  }

  @Test
  void updateCoordinationClearsTicketWhenReferenceIsBlank() {
    when(readModel.loadSignals(any())).thenReturn(List.of(
        signal("2026-03-18T09:58:00Z", "tenant-1", ConfigSetEnvironmentType.DEV, "room-1", "meeting-1", "subject-1", "trace-1", "TOKEN_INVALID", "TOKEN", null, null)));
    when(coordinationPort.update(any())).thenReturn(new AdminIncidentCoordinationPort.CoordinationSnapshot(
        true,
        "available",
        "Coordination seam remains optional.",
        null,
        "triage",
        null,
        "not-linked",
        null,
        List.of()));

    String incidentId = service.listIncidents(
        "tenant-1",
        List.of("ROLE_admin"),
        new AdminIncidentsService.AdminIncidentListQuery("1h", "dev", null, null, null, null, null, null, null, null, 50, 0, "time", "desc"))
        .items()
        .get(0)
        .incidentId();

    service.updateCoordination(
        "tenant-1",
        List.of("ROLE_admin"),
        incidentId,
        "dev",
        new AdminIncidentCoordinationUpdateRequest("   ", "triage", "   ", "not-linked"),
        "admin-user");

    ArgumentCaptor<AdminIncidentCoordinationPort.CoordinationUpdateCommand> commandCaptor =
        ArgumentCaptor.forClass(AdminIncidentCoordinationPort.CoordinationUpdateCommand.class);
    verify(coordinationPort).update(commandCaptor.capture());

    assertThat(commandCaptor.getValue().owner()).isNull();
    assertThat(commandCaptor.getValue().ticketReference()).isNull();
    assertThat(commandCaptor.getValue().ticketStatus()).isEqualTo("not-linked");
  }

    @Test
    void infersCriticalSavedViewWhenSeverityFilterRequestsCriticalQueue() {
        when(readModel.loadSignals(any())).thenReturn(List.of(
                signal("2026-03-18T09:58:00Z", "tenant-1", ConfigSetEnvironmentType.DEV, null, null, "subject-1", "trace-1", "CONFIG_INCOMPATIBLE", "CONFIG", "critical", "blocked")));

        AdminIncidentListResponse response = service.listIncidents(
                "tenant-1",
                List.of("ROLE_admin"),
                new AdminIncidentsService.AdminIncidentListQuery("1h", "dev", null, null, null, null, null, null, null, "critical", 50, 0, "queue", "desc"));

        assertThat(response.selectedView()).isEqualTo("critical");
        assertThat(response.selectedQuickFacet()).isEqualTo("severity:critical");
    }

  private AdminIncidentsReadModel.IncidentSignal signal(
      String occurredAt,
      String tenantId,
      ConfigSetEnvironmentType environmentType,
      String roomId,
      String meetingId,
      String subjectId,
      String traceId,
      String errorCode,
      String category,
      String alertSeverity,
      String joinReadinessStatus) {
    return new AdminIncidentsReadModel.IncidentSignal(
        Instant.parse(occurredAt),
        tenantId,
        environmentType,
        roomId,
        meetingId,
        subjectId,
        traceId,
        traceId,
        errorCode,
        category,
        "participant",
        "diagnostic",
        alertSeverity,
        joinReadinessStatus);
  }

    private AdminIncidentsReadModel.IncidentSignal signalWithoutOptionalEvidence(
            String occurredAt,
            String tenantId,
            ConfigSetEnvironmentType environmentType,
            String roomId,
            String meetingId,
            String subjectId,
            String traceId,
            String errorCode,
            String category,
            String alertSeverity,
            String joinReadinessStatus) {
        return new AdminIncidentsReadModel.IncidentSignal(
                Instant.parse(occurredAt),
                tenantId,
                environmentType,
                roomId,
                meetingId,
                subjectId,
                traceId,
                null,
                errorCode,
                category,
                "participant",
                null,
                alertSeverity,
                joinReadinessStatus);
    }
}