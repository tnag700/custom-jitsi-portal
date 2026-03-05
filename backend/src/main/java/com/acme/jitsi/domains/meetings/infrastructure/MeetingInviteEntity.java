package com.acme.jitsi.domains.meetings.infrastructure;

import com.acme.jitsi.domains.meetings.service.MeetingInvite;
import com.acme.jitsi.domains.meetings.service.MeetingRole;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "meeting_invites")
class MeetingInviteEntity {

  @Id
  @Column(name = "id", length = 36)
  private String id;

  @Column(name = "meeting_id", nullable = false, length = 36)
  private String meetingId;

  @Column(name = "token", nullable = false, unique = true, length = 64)
  private String token;

  @Convert(converter = MeetingRoleConverter.class)
  @Column(name = "role", nullable = false, length = 20)
  private MeetingRole role;

  @Column(name = "max_uses", nullable = false)
  private int maxUses = 1;

  @Column(name = "used_count", nullable = false)
  private int usedCount = 0;

  @Column(name = "expires_at")
  private Instant expiresAt;

  @Column(name = "revoked_at")
  private Instant revokedAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "created_by", nullable = false, updatable = false, length = 100)
  private String createdBy;

  @Column(name = "recipient_email", length = 255)
  private String recipientEmail;

  @Column(name = "recipient_user_id", length = 100)
  private String recipientUserId;

  @Version
  @Column(name = "version", nullable = false)
  private Long version = 0L;

  MeetingInviteEntity() {}

  MeetingInviteEntity(MeetingInvite invite) {
    this.id = invite.id();
    this.meetingId = invite.meetingId();
    this.token = invite.token();
    this.role = invite.role();
    this.maxUses = invite.maxUses();
    this.usedCount = invite.usedCount();
    this.expiresAt = invite.expiresAt();
    this.revokedAt = invite.revokedAt();
    this.createdAt = invite.createdAt();
    this.createdBy = invite.createdBy();
    this.recipientEmail = invite.recipientEmail();
    this.recipientUserId = invite.recipientUserId();
  }

  void updateFrom(MeetingInvite invite) {
    this.role = invite.role();
    this.maxUses = invite.maxUses();
    this.usedCount = invite.usedCount();
    this.expiresAt = invite.expiresAt();
    this.revokedAt = invite.revokedAt();
    this.recipientEmail = invite.recipientEmail();
    this.recipientUserId = invite.recipientUserId();
    this.version = invite.version();
  }

  MeetingInvite toDomain() {
    return new MeetingInvite(
        id,
        meetingId,
        token,
        role,
        maxUses,
        usedCount,
        expiresAt,
        revokedAt,
        createdAt,
        createdBy,
        recipientEmail,
        recipientUserId,
        version
    );
  }

  String getId() { return id; }
  String getMeetingId() { return meetingId; }
  String getToken() { return token; }
  MeetingRole getRole() { return role; }
  int getMaxUses() { return maxUses; }
  int getUsedCount() { return usedCount; }
  Instant getExpiresAt() { return expiresAt; }
  Instant getRevokedAt() { return revokedAt; }
  Instant getCreatedAt() { return createdAt; }
  String getCreatedBy() { return createdBy; }
  String getRecipientEmail() { return recipientEmail; }
  String getRecipientUserId() { return recipientUserId; }
}
