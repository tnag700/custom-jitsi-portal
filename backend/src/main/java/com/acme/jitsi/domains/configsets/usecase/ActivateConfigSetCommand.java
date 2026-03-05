package com.acme.jitsi.domains.configsets.usecase;

public record ActivateConfigSetCommand(
    String configSetId,
    String tenantId,
    String actorId,
    String traceId) {
}