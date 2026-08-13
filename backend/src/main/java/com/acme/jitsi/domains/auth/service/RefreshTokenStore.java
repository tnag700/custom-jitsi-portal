package com.acme.jitsi.domains.auth.service;

import java.time.Instant;

public interface RefreshTokenStore {

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

  ConsumeResult rotate(String tokenId, RefreshTokenState nextState);

  void revoke(String tokenId);
}
