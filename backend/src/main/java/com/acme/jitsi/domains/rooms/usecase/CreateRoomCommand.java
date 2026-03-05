package com.acme.jitsi.domains.rooms.usecase;

public record CreateRoomCommand(
    String name,
    String description,
    String tenantId,
    String configSetId,
    String actorId,
    String traceId) {
}
