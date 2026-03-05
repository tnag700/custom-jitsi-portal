package com.acme.jitsi.domains.meetings.infrastructure;

import com.acme.jitsi.domains.meetings.service.Meeting;
import com.acme.jitsi.domains.meetings.service.MeetingStatus;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "meetings")
@SQLDelete(sql = "UPDATE meetings SET deleted = true WHERE meeting_id = ?")
@SQLRestriction("deleted = false")
class MeetingEntity {

  @Id
  @Column(name = "meeting_id", nullable = false, updatable = false)
  private String meetingId;

  @Column(name = "room_id", nullable = false)
  private String roomId;

  @Column(name = "title", nullable = false)
  private String title;

  @Column(name = "description")
  private String description;

  @Column(name = "meeting_type", nullable = false)
  private String meetingType;

  @Column(name = "config_set_id", nullable = false)
  private String configSetId;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private MeetingStatus status;

  @Column(name = "starts_at", nullable = false)
  private Instant startsAt;

  @Column(name = "ends_at", nullable = false)
  private Instant endsAt;

  @Column(name = "allow_guests", nullable = false)
  private boolean allowGuests;

  @Column(name = "recording_enabled", nullable = false)
  private boolean recordingEnabled;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Column(name = "deleted", nullable = false)
  private boolean deleted = false;

  protected MeetingEntity() {
  }

  MeetingEntity(Meeting meeting) {
    this.meetingId = meeting.meetingId();
    this.roomId = meeting.roomId();
    this.title = meeting.title();
    this.description = meeting.description();
    this.meetingType = meeting.meetingType();
    this.configSetId = meeting.configSetId();
    this.status = meeting.status();
    this.startsAt = meeting.startsAt();
    this.endsAt = meeting.endsAt();
    this.allowGuests = meeting.allowGuests();
    this.recordingEnabled = meeting.recordingEnabled();
    this.createdAt = meeting.createdAt();
    this.updatedAt = meeting.updatedAt();
  }

  Meeting toDomain() {
    return new Meeting(
        meetingId,
        roomId,
        title,
        description,
        meetingType,
        configSetId,
        status,
        startsAt,
        endsAt,
        allowGuests,
        recordingEnabled,
        createdAt,
        updatedAt);
  }

  void updateFrom(Meeting meeting) {
    this.title = meeting.title();
    this.description = meeting.description();
    this.meetingType = meeting.meetingType();
    this.configSetId = meeting.configSetId();
    this.status = meeting.status();
    this.startsAt = meeting.startsAt();
    this.endsAt = meeting.endsAt();
    this.allowGuests = meeting.allowGuests();
    this.recordingEnabled = meeting.recordingEnabled();
    this.updatedAt = meeting.updatedAt();
  }
}