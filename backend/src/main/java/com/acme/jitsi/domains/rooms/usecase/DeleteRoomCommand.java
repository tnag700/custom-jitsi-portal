package com.acme.jitsi.domains.rooms.usecase;

import com.acme.jitsi.domains.rooms.service.Room;

public record DeleteRoomCommand(
    Room existing,
    String actorId,
    String traceId) {
}
