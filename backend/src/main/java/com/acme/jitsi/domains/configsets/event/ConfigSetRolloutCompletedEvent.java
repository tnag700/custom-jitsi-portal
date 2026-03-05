package com.acme.jitsi.domains.configsets.event;

public record ConfigSetRolloutCompletedEvent(
    String rolloutId,
    String configSetId,
    String previousConfigSetId,
    String actorId,
    String traceId,
    String status) {
}