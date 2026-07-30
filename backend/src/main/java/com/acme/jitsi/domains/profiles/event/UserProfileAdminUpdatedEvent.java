package com.acme.jitsi.domains.profiles.event;

import java.util.List;

public record UserProfileAdminUpdatedEvent(
    String profileId,
    String subjectId,
    String tenantId,
    String actorId,
    String traceId,
    List<String> changedFields) {
}
