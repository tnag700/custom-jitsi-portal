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
}
