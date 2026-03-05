package com.acme.jitsi.domains.auth.service;

import java.time.Instant;

interface RefreshTokenStore {

  enum ConsumeStatus {
    CONSUMED,
    USED,
    REVOKED,
    MISSING
  }

  enum TokenStatus {
    ACTIVE,
    USED,
    REVOKED
  }

  record RefreshTokenState(
      String tokenId,
      String subject,
      String meetingId,
      Instant absoluteExpiresAt,
      Instant idleExpiresAt,
      TokenStatus status) {
  }

  record ConsumeResult(ConsumeStatus status, RefreshTokenState state) {
  }

  RefreshTokenState createIfAbsent(RefreshTokenState state);

  ConsumeResult consume(String tokenId);

  void create(RefreshTokenState state);

  void revoke(String tokenId);
}
