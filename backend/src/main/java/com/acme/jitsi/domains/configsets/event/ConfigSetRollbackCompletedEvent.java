package com.acme.jitsi.domains.configsets.event;

public record ConfigSetRollbackCompletedEvent(
    String rolloutId,
    String configSetId,
    String previousConfigSetId,
    String actorId,
    String traceId) {
}