package com.acme.jitsi.domains.configsets.event;

public record ConfigSetCreatedEvent(
    String configSetId,
    String actorId,
    String traceId,
    String changedFields,
    String oldValues,
    String newValues) {
}