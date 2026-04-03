package com.acme.jitsi.domains.admin.service;

import java.util.Locale;
import java.util.Set;

final class IncidentCoordinationNormalizationPolicy {

  private static final Set<String> WORKFLOW_STATUSES = Set.of(
      "triage",
      "investigating",
      "waiting-external",
      "resolved");

  private static final Set<String> TICKET_STATUSES = Set.of(
      "not-linked",
      "linked",
      "waiting-external",
      "resolved");

  private IncidentCoordinationNormalizationPolicy() {
  }

  static String normalizeWorkflowStatus(String workflowStatus) {
    String normalized = IncidentNormalizationPolicy.blankToNull(workflowStatus);
    if (normalized == null) {
      throw new AdminIncidentsInvalidRequestException("workflowStatus обязателен для coordination update.");
    }
    String token = normalized.toLowerCase(Locale.ROOT);
    if (!WORKFLOW_STATUSES.contains(token)) {
      throw new AdminIncidentsInvalidRequestException(
          "workflowStatus должен быть одним из: %s.".formatted(WORKFLOW_STATUSES));
    }
    return token;
  }

  static String normalizeTicketStatus(String ticketReference, String ticketStatus) {
    String reference = IncidentNormalizationPolicy.blankToNull(ticketReference);
    String status = IncidentNormalizationPolicy.blankToNull(ticketStatus);
    return reference == null
        ? normalizeTicketStatusWithoutReference(status)
        : normalizeLinkedTicketStatus(status);
  }

  static String normalizeActorId(String actorId) {
    String normalized = IncidentNormalizationPolicy.blankToNull(actorId);
    return normalized == null ? "admin" : normalized;
  }

  private static String normalizeTicketStatusWithoutReference(String status) {
    boolean hasStatus = status != null;
    boolean notLinkedStatus = "not-linked".equalsIgnoreCase(status);
    if (hasStatus && !notLinkedStatus) {
      throw new AdminIncidentsInvalidRequestException(
          "ticketStatus без ticketReference допускает только значение not-linked.");
    }
    return IncidentNormalizationPolicy.blankToNull(hasStatus ? "not-linked" : "");
  }

  private static String normalizeLinkedTicketStatus(String status) {
    if (status == null) {
      return "linked";
    }
    String token = status.toLowerCase(Locale.ROOT);
    if ("not-linked".equals(token)) {
      throw new AdminIncidentsInvalidRequestException(
          "ticketReference нельзя сохранять со статусом not-linked.");
    }
    if (!TICKET_STATUSES.contains(token)) {
      throw new AdminIncidentsInvalidRequestException(
          "ticketStatus должен быть одним из: %s.".formatted(TICKET_STATUSES));
    }
    return token;
  }
}