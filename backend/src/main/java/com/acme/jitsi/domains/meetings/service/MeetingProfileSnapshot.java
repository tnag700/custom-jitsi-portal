package com.acme.jitsi.domains.meetings.service;

import org.jspecify.annotations.Nullable;

public record MeetingProfileSnapshot(
    String subjectId,
    @Nullable String fullName,
    @Nullable String organization,
    @Nullable String position) {
}