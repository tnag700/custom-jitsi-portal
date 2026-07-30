package com.acme.jitsi.domains.profiles.application;

public record UpdateUserProfileCommand(
    String actorId,
    String subjectId,
    String tenantId,
    String fullName,
    String organization,
    String position,
    String traceId) {
}
