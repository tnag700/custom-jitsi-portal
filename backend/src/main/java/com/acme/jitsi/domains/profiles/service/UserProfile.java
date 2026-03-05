package com.acme.jitsi.domains.profiles.service;

import java.time.Instant;

public record UserProfile(
    String id,
    String subjectId,
    String tenantId,
    String fullName,
    String organization,
    String position,
    Instant createdAt,
    Instant updatedAt) {
}
