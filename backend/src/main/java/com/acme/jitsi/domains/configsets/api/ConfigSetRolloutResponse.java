package com.acme.jitsi.domains.configsets.api;

public record ConfigSetRolloutResponse(
    String rolloutId,
    String configSetId,
    String previousConfigSetId,
    String tenantId,
    String environmentType,
    String status,
    String validationErrors,
    String startedAt,
    String completedAt,
    String actorId) {
}