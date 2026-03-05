package com.acme.jitsi.domains.profiles.application;

public record UpsertProfileCommand(
    String subjectId,
    String tenantId,
    String fullName,
    String organization,
    String position) {
}
