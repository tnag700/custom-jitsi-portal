package com.acme.jitsi.domains.configsets.service;

import java.time.Instant;

public record ConfigSetRollout(
    String rolloutId,
    String configSetId,
    String previousConfigSetId,
    String tenantId,
    ConfigSetEnvironmentType environmentType,
    RolloutStatus status,
    String validationErrors,
    Instant startedAt,
    Instant completedAt,
    String actorId,
    String traceId) {
}