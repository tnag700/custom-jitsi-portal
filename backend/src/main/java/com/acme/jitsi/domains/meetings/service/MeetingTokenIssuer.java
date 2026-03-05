package com.acme.jitsi.domains.meetings.service;

import java.time.Instant;

public interface MeetingTokenIssuer {

  record TokenResult(String joinUrl, Instant expiresAt, String role) {
  }

  record AccessTokenResult(String accessToken, Instant expiresAt, String role) {
  }

  TokenResult issueToken(String meetingId, String subject);

  TokenResult issueGuestToken(String meetingId, String guestSubject);

  AccessTokenResult issueAccessToken(String meetingId, String subject);
}