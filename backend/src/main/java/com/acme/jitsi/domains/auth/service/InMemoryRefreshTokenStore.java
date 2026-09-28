package com.acme.jitsi.domains.auth.service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

class InMemoryRefreshTokenStore implements RefreshTokenStore {

  private record StoredToken(RefreshTokenState state, String familyId) {}

  private final Map<String, StoredToken> tokens = new HashMap<>();

  // ponytail: one monitor protects families in the development-only store; shard only if contention matters.
  @Override
  public synchronized RefreshTokenState createIfAbsent(RefreshTokenState state) {
    cleanupExpired();
    StoredToken existing = tokens.get(state.tokenId());
    if (existing != null) {
      return existing.state();
    }
    if (state.expiredAt(Instant.now())) {
      return state;
    }
    return tokens.computeIfAbsent(state.tokenId(), ignored -> new StoredToken(state, state.tokenId())).state();
  }

  @Override
  public synchronized ConsumeResult consume(String tokenId) {
    return consumeOrRotate(tokenId, null);
  }

  @Override
  public synchronized ConsumeResult rotate(String tokenId, RefreshTokenState nextState) {
    return consumeOrRotate(tokenId, java.util.Objects.requireNonNull(nextState));
  }

  private ConsumeResult consumeOrRotate(String tokenId, RefreshTokenState nextState) {
    cleanupExpired();
    StoredToken stored = tokens.get(tokenId);
    if (stored == null) {
      return new ConsumeResult(ConsumeStatus.MISSING, null);
    }
    RefreshTokenState current = stored.state();
    if (current.status() == TokenStatus.REVOKED) {
      return new ConsumeResult(ConsumeStatus.REVOKED, current);
    }
    if (current.status() == TokenStatus.USED) {
      revokeFamily(stored.familyId());
      return new ConsumeResult(ConsumeStatus.USED, current);
    }
    if (current.expiredAt(Instant.now())) {
      return new ConsumeResult(ConsumeStatus.MISSING, current);
    }
    if (nextState != null) {
      if (tokens.containsKey(nextState.tokenId())) {
        throw new IllegalStateException("A refresh successor token ID already exists.");
      }
      if (!current.subject().equals(nextState.subject()) || !current.meetingId().equals(nextState.meetingId())) {
        throw new IllegalArgumentException("A refresh successor must belong to the same session.");
      }
      Instant idle = nextState.idleExpiresAt().isBefore(current.absoluteExpiresAt())
          ? nextState.idleExpiresAt() : current.absoluteExpiresAt();
      var successor = new RefreshTokenState(nextState.tokenId(), current.subject(), current.meetingId(),
          current.absoluteExpiresAt(), idle, TokenStatus.ACTIVE);
      tokens.put(successor.tokenId(), new StoredToken(successor, stored.familyId()));
    }
    RefreshTokenState used = current.withStatus(TokenStatus.USED);
    tokens.put(tokenId, new StoredToken(used, stored.familyId()));
    return new ConsumeResult(ConsumeStatus.CONSUMED, used);
  }

  @Override
  public synchronized boolean revoke(String tokenId, String subject) {
    cleanupExpired();
    StoredToken stored = tokens.get(tokenId);
    if (stored == null || !stored.state().subject().equals(subject)) {
      return false;
    }
    revokeFamily(stored.familyId());
    return true;
  }

  private void revokeFamily(String familyId) {
    tokens.replaceAll((id, token) -> token.familyId().equals(familyId)
        ? new StoredToken(token.state().withStatus(TokenStatus.REVOKED), familyId) : token);
  }

  synchronized void cleanupExpired() {
    Instant now = Instant.now();
    tokens.values().removeIf(token -> !now.isBefore(token.state().absoluteExpiresAt()));
  }
}
