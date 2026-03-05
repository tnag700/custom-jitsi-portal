package com.acme.jitsi.domains.profiles.event;

public record UserProfileCreatedEvent(
    String profileId,
    String subjectId,
    String tenantId,
    String fullName) {
}
