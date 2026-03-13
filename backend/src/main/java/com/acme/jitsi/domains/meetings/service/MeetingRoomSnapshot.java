package com.acme.jitsi.domains.meetings.service;

public record MeetingRoomSnapshot(
    String roomId,
    String name,
    String tenantId,
    String configSetId,
    boolean active,
    boolean configSetValid) {
}