package com.acme.jitsi.domains.meetings.service;

public record BulkInviteRecipient(
    int rowIndex,
    String email,
    String userId,
    MeetingRole roleOverride
) {
  public String normalizedEmail() {
    return email == null ? null : email.trim().toLowerCase();
  }

  public String normalizedUserId() {
    return userId == null ? null : userId.trim();
  }

  public String displayRecipient() {
    if (normalizedEmail() != null && !normalizedEmail().isBlank()) {
      return normalizedEmail();
    }
    if (normalizedUserId() != null && !normalizedUserId().isBlank()) {
      return normalizedUserId();
    }
    return "";
  }
}
