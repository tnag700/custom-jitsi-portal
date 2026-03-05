package com.acme.jitsi.domains.meetings.service;

import java.time.Instant;

public record MeetingParticipantAssignment(
    String assignmentId,
    String meetingId,
    String subjectId,
    MeetingRole role,
    Instant assignedAt,
    String assignedBy,
    Instant createdAt,
    Instant updatedAt
) {
  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
    private String assignmentId;
    private String meetingId;
    private String subjectId;
    private MeetingRole role;
    private Instant assignedAt;
    private String assignedBy;
    private Instant createdAt;
    private Instant updatedAt;

    public Builder assignmentId(String assignmentId) {
      this.assignmentId = assignmentId;
      return this;
    }

    public Builder meetingId(String meetingId) {
      this.meetingId = meetingId;
      return this;
    }

    public Builder subjectId(String subjectId) {
      this.subjectId = subjectId;
      return this;
    }

    public Builder role(MeetingRole role) {
      this.role = role;
      return this;
    }

    public Builder assignedAt(Instant assignedAt) {
      this.assignedAt = assignedAt;
      return this;
    }

    public Builder assignedBy(String assignedBy) {
      this.assignedBy = assignedBy;
      return this;
    }

    public Builder createdAt(Instant createdAt) {
      this.createdAt = createdAt;
      return this;
    }

    public Builder updatedAt(Instant updatedAt) {
      this.updatedAt = updatedAt;
      return this;
    }

    public MeetingParticipantAssignment build() {
      return new MeetingParticipantAssignment(
          assignmentId,
          meetingId,
          subjectId,
          role,
          assignedAt,
          assignedBy,
          createdAt,
          updatedAt
      );
    }
  }
}
