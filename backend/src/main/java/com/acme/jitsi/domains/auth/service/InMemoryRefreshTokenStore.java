package com.acme.jitsi.domains.auth.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

class InMemoryRefreshTokenStore implements RefreshTokenStore {

  private final Map<String, RefreshTokenState> tokens = new ConcurrentHashMap<>();

  @Override
  public RefreshTokenState createIfAbsent(RefreshTokenState state) {
    return tokens.computeIfAbsent(state.tokenId(), ignored -> state);
  }

  @Override
  public ConsumeResult consume(String tokenId) {
    final ConsumeStatus[] statusHolder = {ConsumeStatus.MISSING};
    final RefreshTokenState[] stateHolder = {null};

    tokens.compute(tokenId, (ignored, existing) -> {
      if (existing == null) {
        statusHolder[0] = ConsumeStatus.MISSING;
        stateHolder[0] = null;
        return null;
      }

      stateHolder[0] = existing;
      if (existing.status() == TokenStatus.REVOKED) {
        statusHolder[0] = ConsumeStatus.REVOKED;
        return existing;
      }
      if (existing.status() == TokenStatus.USED) {
        statusHolder[0] = ConsumeStatus.USED;
        return existing;
      }

      statusHolder[0] = ConsumeStatus.CONSUMED;
      return new RefreshTokenState(
          existing.tokenId(),
          existing.subject(),
          existing.meetingId(),
          existing.absoluteExpiresAt(),
          existing.idleExpiresAt(),
          TokenStatus.USED);
    });

    return new ConsumeResult(statusHolder[0], stateHolder[0]);
  }

  @Override
  public void create(RefreshTokenState state) {
    tokens.put(state.tokenId(), state);
  }

  @Override
  public void revoke(String tokenId) {
    tokens.compute(tokenId, (ignored, existing) -> {
      if (existing == null) {
        Instant placeholderExpiry = Instant.now().plus(30, ChronoUnit.DAYS);
        return new RefreshTokenState(
            tokenId,
            "",
            "",
            placeholderExpiry,
            placeholderExpiry,
            TokenStatus.REVOKED);
      }
      return new RefreshTokenState(
          existing.tokenId(),
          existing.subject(),
          existing.meetingId(),
          existing.absoluteExpiresAt(),
          existing.idleExpiresAt(),
          TokenStatus.REVOKED);
    });
  }
}
