package com.acme.jitsi.domains.configsets.api;

import java.time.Instant;

record ConfigSetResponse(
    String configSetId,
    String name,
    String tenantId,
    String environmentType,
    String issuer,
    String audience,
    String algorithm,
    String roleClaim,
    String signingSecret,
    String jwksUri,
    int accessTtlMinutes,
    Integer refreshTtlMinutes,
    String meetingsServiceUrl,
    String status,
    Instant createdAt,
    Instant updatedAt) {
}