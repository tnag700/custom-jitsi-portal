package com.acme.jitsi.domains.meetings.service;

import java.time.Instant;

public record MeetingInvite(
    String id,
    String meetingId,
    String token,
    MeetingRole role,
    int maxUses,
    int usedCount,
    Instant expiresAt,
    Instant revokedAt,
    Instant createdAt,
    String createdBy,
    String recipientEmail,
    String recipientUserId,
    long version
) {
  public MeetingInvite(
      String id,
      String meetingId,
      String token,
      MeetingRole role,
      int maxUses,
      int usedCount,
      Instant expiresAt,
      Instant revokedAt,
      Instant createdAt,
      String createdBy) {
    this(id, meetingId, token, role, maxUses, usedCount, expiresAt, revokedAt, createdAt, createdBy, null, null, 0L);
  }

  public boolean isValid() {
    return isValid(Instant.now());
  }

  public boolean isValid(Instant now) {
    return revokedAt == null
        && (expiresAt == null || now.isBefore(expiresAt))
        && usedCount < maxUses;
  }

  public boolean isExpired() {
    return isExpired(Instant.now());
  }

  public boolean isExpired(Instant now) {
    return expiresAt != null && now.isAfter(expiresAt);
  }

  public boolean isRevoked() {
    return revokedAt != null;
  }

  public boolean isExhausted() {
    return usedCount >= maxUses;
  }

  public MeetingInvite withUsedCount(int newCount) {
    return new MeetingInvite(
        id, meetingId, token, role, maxUses, newCount,
        expiresAt, revokedAt, createdAt, createdBy, recipientEmail, recipientUserId, version
    );
  }

  public MeetingInvite withRevoked() {
    return new MeetingInvite(
        id, meetingId, token, role, maxUses, usedCount,
        expiresAt, Instant.now(), createdAt, createdBy, recipientEmail, recipientUserId, version
    );
  }
}
