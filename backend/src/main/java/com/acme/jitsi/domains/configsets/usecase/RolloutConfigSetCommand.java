package com.acme.jitsi.domains.configsets.usecase;

public record RolloutConfigSetCommand(
    String configSetId,
    String tenantId,
    String actorId,
    String traceId) {
}