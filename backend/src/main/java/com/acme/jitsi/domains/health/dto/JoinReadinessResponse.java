package com.acme.jitsi.domains.health.dto;

import java.util.List;

public record JoinReadinessResponse(
    String status,
    String checkedAt,
    String traceId,
    String publicJoinUrl,
    List<JoinReadinessCheckResponse> systemChecks) {
}