package com.acme.jitsi.domains.rooms.usecase;

import com.acme.jitsi.domains.rooms.service.Room;

public record UpdateRoomCommand(
    Room existing,
    String name,
    String description,
    String configSetId,
    String actorId,
    String traceId) {
}
