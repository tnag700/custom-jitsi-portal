package com.acme.jitsi.domains.configsets.service;

import java.time.Instant;

public record ConfigSet(
    String configSetId,
    String name,
    String tenantId,
    ConfigSetEnvironmentType environmentType,
    String issuer,
    String audience,
    String algorithm,
    String roleClaim,
    String signingSecret,
    String jwksUri,
    int accessTtlMinutes,
    Integer refreshTtlMinutes,
    String meetingsServiceUrl,
    ConfigSetStatus status,
    Instant createdAt,
    Instant updatedAt) {
}