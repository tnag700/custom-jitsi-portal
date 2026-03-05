package com.acme.jitsi.domains.configsets.usecase;

import com.acme.jitsi.domains.configsets.service.ConfigSetEnvironmentType;

public record CreateConfigSetCommand(
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
    String actorId,
    String traceId) {
}