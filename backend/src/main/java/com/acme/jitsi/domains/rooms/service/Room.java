package com.acme.jitsi.domains.rooms.service;

import java.time.Instant;

public record Room(
    String roomId,
    String name,
    String description,
    String tenantId,
    String configSetId,
    RoomStatus status,
    Instant createdAt,
    Instant updatedAt) {
}
