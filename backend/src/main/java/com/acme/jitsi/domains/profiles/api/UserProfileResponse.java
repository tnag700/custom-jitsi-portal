package com.acme.jitsi.domains.profiles.api;

import java.time.Instant;

record UserProfileResponse(
    String subjectId,
    String tenantId,
    String fullName,
    String organization,
    String position,
    Instant createdAt,
    Instant updatedAt) {
}
