package com.acme.jitsi.domains.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class InMemoryRefreshTokenStoreTest {

  @Test
  void revokeCreatesRevokedMarkerForUnknownTokenId() {
    InMemoryRefreshTokenStore store = new InMemoryRefreshTokenStore();

    store.revoke("missing-token-id-1");

    RefreshTokenStore.ConsumeResult consumeResult = store.consume("missing-token-id-1");

    assertThat(consumeResult.status()).isEqualTo(RefreshTokenStore.ConsumeStatus.REVOKED);
    assertThat(consumeResult.state()).isNotNull();
    assertThat(consumeResult.state().tokenId()).isEqualTo("missing-token-id-1");
    assertThat(consumeResult.state().status()).isEqualTo(RefreshTokenStore.TokenStatus.REVOKED);
    assertThat(consumeResult.state().absoluteExpiresAt()).isAfter(Instant.now());
  }

  @Test
  void rotateConsumesCurrentTokenAndCreatesSuccessorAsOneCriticalSection() {
    InMemoryRefreshTokenStore store = new InMemoryRefreshTokenStore();
    Instant absoluteExpiry = Instant.now().plusSeconds(7200);
    RefreshTokenStore.RefreshTokenState current = new RefreshTokenStore.RefreshTokenState(
        "current-token",
        "user-1",
        "meeting-1",
        absoluteExpiry,
        Instant.now().plusSeconds(3600),
        RefreshTokenStore.TokenStatus.ACTIVE);
    RefreshTokenStore.RefreshTokenState successor = new RefreshTokenStore.RefreshTokenState(
        "successor-token",
        "user-1",
        "meeting-1",
        absoluteExpiry,
        Instant.now().plusSeconds(3600),
        RefreshTokenStore.TokenStatus.ACTIVE);
    store.createIfAbsent(current);

    RefreshTokenStore.ConsumeResult result = store.rotate("current-token", successor);

    assertThat(result.status()).isEqualTo(RefreshTokenStore.ConsumeStatus.CONSUMED);
    assertThat(store.rotate("current-token", successor).status())
        .isEqualTo(RefreshTokenStore.ConsumeStatus.USED);
    assertThat(store.consume("successor-token").status())
        .isEqualTo(RefreshTokenStore.ConsumeStatus.CONSUMED);
  }
}
