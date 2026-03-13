package com.acme.jitsi.domains.invites.service;

import java.time.Instant;

public interface InviteJoinPort {

  record JoinResult(String joinUrl, Instant expiresAt, String role) {
  }

  JoinResult issueGuestJoin(String meetingId, String guestSubject);
}