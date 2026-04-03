package com.acme.jitsi.domains.admin.service;

public interface AdminIncidentTicketPort {

  TicketingStatus describeTicketing(TicketContext context);

  TicketCreationResult createTicket(TicketContext context);

  record TicketContext(
      String incidentId,
      String tenantId,
      String environment,
      String errorCode,
      String category,
      String traceId,
      String summary) {
  }

  record TicketingStatus(
      boolean available,
      String ticketKey,
      String ticketUrl,
      String status) {
  }

  record TicketCreationResult(
      boolean available,
      boolean created,
      String ticketKey,
      String ticketUrl,
      String summary,
      String message) {
  }
}