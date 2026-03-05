package com.acme.jitsi.domains.configsets.event;

public record ConfigSetDeactivatedEvent(
    String configSetId,
    String actorId,
    String traceId,
    String changedFields,
    String oldValues,
    String newValues) {
}