package com.acme.jitsi.domains.profiles.event;

public record UserProfileUpdatedEvent(
    String profileId,
    String subjectId,
    String tenantId,
    String fullName) {
}
