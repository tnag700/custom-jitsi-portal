package com.acme.jitsi.domains.configsets.api;

import java.util.List;

public record ConfigSetCompatibilityResponse(
    String status,
    List<ConfigSetCompatibilityMismatchResponse> mismatches,
    String checkedAt,
    String traceId) {
}