package com.acme.jitsi.domains.meetings.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

record CreateMeetingRequest(
    @NotBlank(message = "Meeting title is required")
    @Size(min = 1, max = 255, message = "Meeting title must be between 1 and 255 characters")
    String title,
    @Size(max = 1000, message = "Description must not exceed 1000 characters")
    String description,
    @NotBlank(message = "Meeting type is required")
    String meetingType,
    @NotNull(message = "Meeting start time is required")
    Instant startsAt,
    @NotNull(message = "Meeting end time is required")
    Instant endsAt,
    Boolean allowGuests,
    Boolean recordingEnabled) {

  boolean resolvedAllowGuests() {
    return Boolean.TRUE.equals(allowGuests);
  }

  boolean resolvedRecordingEnabled() {
    return Boolean.TRUE.equals(recordingEnabled);
  }
}