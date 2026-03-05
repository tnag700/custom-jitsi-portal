package com.acme.jitsi.security;

import java.time.Instant;
import java.util.Objects;

record JwtStartupValidationEvent(
    JwtStartupValidationEventType eventType,
    JwtStartupValidationErrorCode errorCode,
    String message,
    Instant occurredAt,
    String correlationId) {

    JwtStartupValidationEvent {
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(errorCode, "errorCode");
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(correlationId, "correlationId");
    }
}