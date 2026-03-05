package com.acme.jitsi.domains.meetings.service;

import java.time.Instant;

public record Meeting(
    String meetingId,
    String roomId,
    String title,
    String description,
    String meetingType,
    String configSetId,
    MeetingStatus status,
    Instant startsAt,
    Instant endsAt,
    boolean allowGuests,
    boolean recordingEnabled,
    Instant createdAt,
    Instant updatedAt) {

  /**
   * Returns a copy of this meeting with the given status and updatedAt timestamp.
   * Use instead of manually reconstructing the full record when only status changes.
   */
  public Meeting withStatus(MeetingStatus newStatus, Instant newUpdatedAt) {
    return new Meeting(
        meetingId, roomId, title, description, meetingType, configSetId,
        newStatus, startsAt, endsAt, allowGuests, recordingEnabled, createdAt, newUpdatedAt);
  }

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
    private String meetingId;
    private String roomId;
    private String title;
    private String description;
    private String meetingType;
    private String configSetId;
    private MeetingStatus status;
    private Instant startsAt;
    private Instant endsAt;
    private boolean allowGuests;
    private boolean recordingEnabled;
    private Instant createdAt;
    private Instant updatedAt;

    public Builder meetingId(String meetingId) { this.meetingId = meetingId; return this; }
    public Builder roomId(String roomId) { this.roomId = roomId; return this; }
    public Builder title(String title) { this.title = title; return this; }
    public Builder description(String description) { this.description = description; return this; }
    public Builder meetingType(String meetingType) { this.meetingType = meetingType; return this; }
    public Builder configSetId(String configSetId) { this.configSetId = configSetId; return this; }
    public Builder status(MeetingStatus status) { this.status = status; return this; }
    public Builder startsAt(Instant startsAt) { this.startsAt = startsAt; return this; }
    public Builder endsAt(Instant endsAt) { this.endsAt = endsAt; return this; }
    public Builder allowGuests(boolean allowGuests) { this.allowGuests = allowGuests; return this; }
    public Builder recordingEnabled(boolean recordingEnabled) { this.recordingEnabled = recordingEnabled; return this; }
    public Builder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }
    public Builder updatedAt(Instant updatedAt) { this.updatedAt = updatedAt; return this; }

    public Meeting build() {
      return new Meeting(meetingId, roomId, title, description, meetingType, configSetId,
          status, startsAt, endsAt, allowGuests, recordingEnabled, createdAt, updatedAt);
    }
  }
}