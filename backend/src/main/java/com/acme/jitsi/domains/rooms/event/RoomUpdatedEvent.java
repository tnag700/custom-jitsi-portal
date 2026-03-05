package com.acme.jitsi.domains.rooms.event;

public record RoomUpdatedEvent(
    String roomId,
    String actorId,
    String traceId,
    String changedFields,
    String oldValues,
    String newValues
) {}
