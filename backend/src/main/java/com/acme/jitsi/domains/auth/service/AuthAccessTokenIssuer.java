package com.acme.jitsi.domains.auth.service;

import java.time.Instant;

public interface AuthAccessTokenIssuer {

  record AccessTokenResult(String accessToken, Instant expiresAt, String role) {
  }

  AccessTokenResult issueAccessToken(String meetingId, String subject);
}