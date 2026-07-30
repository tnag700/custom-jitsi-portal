package com.acme.jitsi.domains.admin.service;

import com.acme.jitsi.domains.admin.dto.AdminIncidentTicketResponse;

final class AdminIncidentTicketPolicy {

  private AdminIncidentTicketPolicy() {
  }

  static AdminIncidentTicketResponse createTicket(
      AdminIncidentTicketPort ticketPort,
      AdminIncidentCoordinationPort coordinationPort,
      AdminIncidentAggregate incident,
      String actorId) {
    AdminIncidentTicketPort.TicketCreationResult result =
        ticketPort.createTicket(AdminIncidentDetailPolicy.toTicketContext(incident));
    if (IncidentNormalizationPolicy.hasText(result.ticketKey())
        || IncidentNormalizationPolicy.hasText(result.ticketUrl())) {
      coordinationPort.recordTicketLink(
          new AdminIncidentCoordinationPort.TicketLinkCommand(
              AdminIncidentDetailPolicy.toCoordinationContext(incident),
              IncidentCoordinationNormalizationPolicy.normalizeActorId(actorId),
              result.ticketKey(),
              result.created() ? "linked" : "available",
              result.ticketUrl(),
              AdminIncidentDetailPolicy.incidentTraceReference(incident)));
    }
    return new AdminIncidentTicketResponse(
        result.available(),
        result.created(),
        result.ticketKey(),
        result.ticketUrl(),
        result.summary(),
        result.message());
  }
}
