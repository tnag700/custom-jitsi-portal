package com.acme.jitsi.domains.meetings.api;

import jakarta.validation.constraints.Size;
import java.time.Instant;
import com.acme.jitsi.shared.validation.TextInputNormalizer;

record UpdateMeetingRequest(
    @Size(min = 1, max = 255, message = "Meeting title must be between 1 and 255 characters")
    String title,
    @Size(max = 1000, message = "Description must not exceed 1000 characters")
    String description,
    String meetingType,
    Instant startsAt,
    Instant endsAt,
    Boolean allowGuests,
    Boolean recordingEnabled) {

    UpdateMeetingRequest {
        title = TextInputNormalizer.normalizeNullable(title);
        description = TextInputNormalizer.normalizeOptional(description);
        meetingType = TextInputNormalizer.normalizeNullable(meetingType);
    }
}