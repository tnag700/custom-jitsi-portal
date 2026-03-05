package com.acme.jitsi.domains.configsets.usecase;

public record DeactivateConfigSetCommand(
    String configSetId,
    String tenantId,
    String actorId,
    String traceId) {
}