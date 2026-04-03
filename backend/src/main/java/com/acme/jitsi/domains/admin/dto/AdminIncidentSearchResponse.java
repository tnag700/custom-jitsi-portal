package com.acme.jitsi.domains.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.jspecify.annotations.Nullable;

public record AdminIncidentSearchResponse(
    String outcome,
  @Schema(types = {"string", "null"})
  @Nullable String incidentId,
  @Schema(types = {"string", "null"})
  @Nullable String detailUrl,
  @Schema(types = {"string", "null"})
  @Nullable String message,
    List<SearchCandidate> candidates) {

  public record SearchCandidate(
      String incidentId,
      String occurredAt,
      String errorCode,
      @Schema(types = {"string", "null"})
      @Nullable String meetingId) {
  }
}