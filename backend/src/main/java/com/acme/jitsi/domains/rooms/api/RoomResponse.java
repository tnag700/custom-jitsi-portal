package com.acme.jitsi.domains.rooms.api;

import java.time.Instant;

record RoomResponse(
    String roomId,
    String name,
    String description,
    String tenantId,
    String configSetId,
    String status,
    Instant createdAt,
    Instant updatedAt) {
}
