package com.acme.jitsi.domains.configsets.usecase;

import com.acme.jitsi.domains.configsets.service.ConfigSetEnvironmentType;

public record RollbackConfigSetCommand(
    String configSetId,
    String tenantId,
    ConfigSetEnvironmentType environmentType,
    String actorId,
    String traceId) {
}