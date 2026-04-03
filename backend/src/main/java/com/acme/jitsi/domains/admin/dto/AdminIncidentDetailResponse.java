package com.acme.jitsi.domains.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.jspecify.annotations.Nullable;

public record AdminIncidentDetailResponse(
    String incidentId,
    String tenantId,
    String environment,
    String errorCode,
    String category,
    String severity,
    String summary,
    String startedAt,
    String endedAt,
    List<AffectedAttempt> affectedAttempts,
    SummaryBar summaryBar,
    List<TimelineEntry> timeline,
    List<EvidenceBlock> evidence,
    List<RelatedLink> relatedLinks,
    List<NextAction> nextActions,
    CoordinationState coordination,
    TicketingState ticketing) {

  public record AffectedAttempt(
      String occurredAt,
      @Schema(types = {"string", "null"})
      @Nullable String traceId,
      @Schema(types = {"string", "null"})
      @Nullable String correlationId,
      @Schema(types = {"string", "null"})
      @Nullable String subjectDisplay,
      @Schema(types = {"string", "null"})
      @Nullable String subjectIdFilterValue,
      @Schema(types = {"string", "null"})
      @Nullable String role,
      @Schema(types = {"string", "null"})
      @Nullable String diagnosticResult,
      @Schema(types = {"string", "null"})
      @Nullable String roomId,
      @Schema(types = {"string", "null"})
      @Nullable String meetingId,
      @Schema(types = {"string", "null"})
      @Nullable String traceUrl) {
  }

  public record SummaryBar(
      String title,
      String refusalReason,
      String affectedScope,
      String operationalStatus,
      String timeWindow,
      String environment) {
  }

  public record TimelineEntry(
      String occurredAt,
      String title,
      String summary,
      @Schema(types = {"string", "null"})
      @Nullable String subjectDisplay,
      @Schema(types = {"string", "null"})
      @Nullable String role,
      @Schema(types = {"string", "null"})
      @Nullable String traceId,
      @Schema(types = {"string", "null"})
      @Nullable String correlationId,
      @Schema(types = {"string", "null"})
      @Nullable String roomId,
      @Schema(types = {"string", "null"})
      @Nullable String meetingId) {
  }

  public record EmptyState(
      String title,
      String detail,
      String nextActionLabel,
      String nextActionTarget) {
  }

  public record EvidenceBlock(
      String kind,
      String title,
      String status,
      @Schema(types = {"string", "null"})
      @Nullable String summary,
      String detail,
      @Schema(types = {"string", "null"})
      @Nullable String traceId,
      @Schema(types = {"string", "null"})
      @Nullable String correlationId,
      @Schema(types = {"string", "null"})
      @Nullable String traceUrl,
      @Schema(types = {"object", "null"})
      @Nullable EmptyState emptyState) {
  }

  public record RelatedLink(
      String kind,
      String label,
      String environment,
      @Schema(types = {"string", "null"})
      @Nullable String subjectId,
      @Schema(types = {"string", "null"})
      @Nullable String roomId,
      @Schema(types = {"string", "null"})
      @Nullable String meetingId,
      @Schema(types = {"string", "null"})
      @Nullable String traceId,
      @Schema(types = {"string", "null"})
      @Nullable String externalUrl) {
  }

  public record NextAction(
      String kind,
      String label,
      String detail,
      String target,
      @Schema(types = {"string", "null"})
      @Nullable String externalUrl) {
  }

  public record CoordinationAuditEntry(
      String occurredAt,
      String actorId,
      String actionType,
      @Schema(types = {"string", "null"})
      @Nullable String traceId,
      String fromState,
      String toState) {
  }

  public record CoordinationState(
      boolean enabled,
      String availability,
      String explanation,
      @Schema(types = {"string", "null"})
      @Nullable String owner,
      String workflowStatus,
      @Schema(types = {"string", "null"})
      @Nullable String ticketReference,
      String ticketStatus,
      @Schema(types = {"string", "null"})
      @Nullable String ticketUrl,
      List<CoordinationAuditEntry> history) {
  }

  public record TicketingState(
      boolean available,
      @Schema(types = {"string", "null"})
      @Nullable String ticketKey,
      @Schema(types = {"string", "null"})
      @Nullable String ticketUrl,
      String status) {
  }
}