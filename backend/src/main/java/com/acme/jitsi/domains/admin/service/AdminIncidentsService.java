package com.acme.jitsi.domains.admin.service;

import com.acme.jitsi.domains.admin.dto.AdminIncidentCoordinationUpdateRequest;
import com.acme.jitsi.domains.admin.dto.AdminIncidentDetailResponse;
import com.acme.jitsi.domains.admin.dto.AdminIncidentListResponse;
import com.acme.jitsi.domains.admin.dto.AdminIncidentSearchResponse;
import com.acme.jitsi.domains.admin.dto.AdminIncidentTicketResponse;
import com.acme.jitsi.domains.configsets.service.ConfigSetEnvironmentType;
import java.time.Clock;
import java.time.Duration;
import java.util.Collection;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AdminIncidentsService {

  private final AdminIncidentsReadModel readModel;
  private final AdminIncidentTicketPort ticketPort;
  private final AdminIncidentCoordinationPort coordinationPort;
  private final Clock clock;
  private final String traceUrlTemplate;
  private final Duration retentionWindow;

  public AdminIncidentsService(
      AdminIncidentsReadModel readModel,
      AdminIncidentTicketPort ticketPort,
      AdminIncidentCoordinationPort coordinationPort,
      Clock clock,
      @Value("${app.admin.trace-url-template:}") String traceUrlTemplate,
      @Value("${app.admin.incidents.retention:PT168H}") String retentionWindow) {
    this.readModel = readModel;
    this.ticketPort = ticketPort;
    this.coordinationPort = coordinationPort;
    this.clock = clock;
    this.traceUrlTemplate = traceUrlTemplate;
    this.retentionWindow = Duration.parse(retentionWindow);
  }

  public AdminIncidentListResponse listIncidents(
      String tenantId,
      Collection<String> authorities,
      AdminIncidentListQuery query) {
    return AdminIncidentListPolicy.list(readModel, tenantId, query, clock);
  }

  public AdminIncidentDetailResponse getIncidentDetail(
      String tenantId,
      Collection<String> authorities,
      String incidentId,
      String environment) {
    AdminIncidentAggregate incident =
        loadIncidentById(tenantId, environment, incidentId);
    AdminIncidentTicketPort.TicketingStatus ticketing =
        ticketPort.describeTicketing(
            AdminIncidentDetailPolicy.toTicketContext(incident));
    AdminIncidentCoordinationPort.CoordinationSnapshot coordination =
        coordinationPort.describe(
            AdminIncidentDetailPolicy.toCoordinationContext(incident));
    boolean fullSubject = IncidentSubjectPolicy.canViewFullSubject(authorities);
    return AdminIncidentDetailPolicy.resolveResponse(
        incident,
        tenantId,
        fullSubject,
        traceUrlTemplate,
        coordination,
        ticketing);
  }

  public AdminIncidentDetailResponse.CoordinationState updateCoordination(
      String tenantId,
      Collection<String> authorities,
      String incidentId,
      String environment,
      AdminIncidentCoordinationUpdateRequest request,
      String actorId) {
    AdminIncidentAggregate incident =
        loadIncidentById(tenantId, environment, incidentId);
    AdminIncidentCoordinationPort.CoordinationSnapshot snapshot =
        coordinationPort.update(
            new AdminIncidentCoordinationPort.CoordinationUpdateCommand(
                AdminIncidentDetailPolicy.toCoordinationContext(incident),
                IncidentCoordinationNormalizationPolicy.normalizeActorId(actorId),
                IncidentNormalizationPolicy.blankToNull(request.owner()),
                IncidentCoordinationNormalizationPolicy.normalizeWorkflowStatus(
                    request.workflowStatus()),
                IncidentNormalizationPolicy.blankToNull(
                    request.ticketReference()),
                IncidentCoordinationNormalizationPolicy.normalizeTicketStatus(
                    request.ticketReference(),
                    request.ticketStatus()),
                AdminIncidentDetailPolicy.incidentTraceReference(incident)));
    return AdminIncidentDetailPolicy.toCoordinationState(snapshot);
  }

  public AdminIncidentSearchResponse searchIncidents(
      String tenantId,
      Collection<String> authorities,
      AdminIncidentSearchQuery query) {
    return AdminIncidentSearchPolicy.search(
        readModel,
        tenantId,
        query,
        clock,
        retentionWindow);
  }

  public AdminIncidentTicketResponse createTicket(
      String tenantId,
      Collection<String> authorities,
      String incidentId,
      String environment,
      String actorId) {
    AdminIncidentAggregate incident =
        loadIncidentById(tenantId, environment, incidentId);
    return AdminIncidentTicketPolicy.createTicket(
        ticketPort,
        coordinationPort,
        incident,
        actorId);
  }

  private AdminIncidentAggregate loadIncidentById(
      String tenantId,
      String environment,
      String incidentId) {
    ConfigSetEnvironmentType environmentType =
        IncidentEnvironmentPolicy.resolveEnvironment(environment);
    return AdminIncidentAggregationPolicy.loadById(
        readModel,
        tenantId,
        environmentType,
        clock,
        retentionWindow,
        incidentId);
  }

  public record AdminIncidentListQuery(
      String period,
      String environment,
      String savedView,
      String quickFacet,
      String roomId,
      String meetingId,
      String subjectId,
      String errorCode,
      String category,
      String severity,
      int limit,
      int offset,
      String sortBy,
      String direction) {
  }

  public record AdminIncidentSearchQuery(
      String environment,
      String traceId,
      String requestId,
      String errorCode,
      String from,
      String to,
      String meetingId) {
  }
}
