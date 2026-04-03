package com.acme.jitsi.domains.admin.service;

import java.util.List;
import org.jspecify.annotations.Nullable;

public interface AdminIncidentCoordinationPort {

  CoordinationSnapshot describe(CoordinationContext context);

  CoordinationSnapshot update(CoordinationUpdateCommand command);

  CoordinationSnapshot recordTicketLink(TicketLinkCommand command);

  record CoordinationContext(
      String incidentId,
      String tenantId,
      String environment) {
  }

  record CoordinationUpdateCommand(
      CoordinationContext context,
      String actorId,
      @Nullable String owner,
      String workflowStatus,
      @Nullable String ticketReference,
      @Nullable String ticketStatus,
      @Nullable String traceId) {
  }

  record TicketLinkCommand(
      CoordinationContext context,
      String actorId,
      @Nullable String ticketReference,
      String ticketStatus,
      @Nullable String ticketUrl,
      @Nullable String traceId) {
  }

  record CoordinationSnapshot(
      boolean enabled,
      String availability,
      String explanation,
      @Nullable String owner,
      String workflowStatus,
      @Nullable String ticketReference,
      String ticketStatus,
      @Nullable String ticketUrl,
      List<CoordinationAuditEntry> history) {
  }

  record CoordinationAuditEntry(
      String occurredAt,
      String actorId,
      String actionType,
      @Nullable String traceId,
      String fromState,
      String toState) {
  }
}