package com.acme.jitsi.domains.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.jspecify.annotations.Nullable;

public record AdminIncidentListResponse(
    String period,
    String environment,
    String tenantId,
    String generatedAt,
  String selectedView,
  @Schema(types = {"string", "null"})
  @Nullable String selectedQuickFacet,
  List<SavedView> availableViews,
  List<QuickFacet> quickFacets,
  QueueSort sort,
    int pageSize,
    int offset,
    long totalElements,
    List<IncidentListItem> items) {

  public record SavedView(
    String token,
    String label,
    String summary) {
  }

  public record QuickFacet(
    String token,
    String label,
    long count,
    boolean active) {
  }

  public record QueueSort(
    String token,
    String label,
    String direction) {
  }

  public record IncidentListItem(
      String incidentId,
      String occurredAt,
      String errorCode,
      String category,
      String tenantId,
      @Schema(types = {"string", "null"})
      @Nullable String roomId,
      @Schema(types = {"string", "null"})
      @Nullable String meetingId,
      long affectedSubjects,
      String severity,
      String affectedEntitySummary,
      String freshnessHint) {
  }
}