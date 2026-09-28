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
    public RefreshTokenState withStatus(TokenStatus nextStatus) {
      return new RefreshTokenState(tokenId, subject, meetingId, absoluteExpiresAt, idleExpiresAt, nextStatus);
    }

    public boolean expiredAt(Instant now) {
      return !now.isBefore(absoluteExpiresAt) || !now.isBefore(idleExpiresAt);
    }
  }

  record ConsumeResult(ConsumeStatus status, RefreshTokenState state) {
  }

  RefreshTokenState createIfAbsent(RefreshTokenState state);

  ConsumeResult consume(String tokenId);

  ConsumeResult rotate(String tokenId, RefreshTokenState nextState);

  boolean revoke(String tokenId, String subject);

  default Instant acceptIssuedAfter() {
    return Instant.EPOCH;
  }
}
