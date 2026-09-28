package com.acme.jitsi.domains.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class InMemoryRefreshTokenStoreTest {

  @Test
  void successorCollisionLeavesBothSessionsActive() {
    var store = new InMemoryRefreshTokenStore();
    Instant now = Instant.now();
    var current = new RefreshTokenStore.RefreshTokenState("current", "owner", "meeting", now.plusSeconds(7200),
        now.plusSeconds(3600), RefreshTokenStore.TokenStatus.ACTIVE);
    var incumbent = new RefreshTokenStore.RefreshTokenState("incumbent", "other", "other-meeting", now.plusSeconds(7200),
        now.plusSeconds(3600), RefreshTokenStore.TokenStatus.ACTIVE);
    store.createIfAbsent(current);
    store.createIfAbsent(incumbent);

    assertThatThrownBy(() -> store.rotate("current", incumbent)).isInstanceOf(IllegalStateException.class);
    assertThat(store.createIfAbsent(current).status()).isEqualTo(RefreshTokenStore.TokenStatus.ACTIVE);
    assertThat(store.createIfAbsent(incumbent).status()).isEqualTo(RefreshTokenStore.TokenStatus.ACTIVE);
  }

  @Test
  void onlyTheOwnerCanRevokeAKnownSession() {
    var store = new InMemoryRefreshTokenStore();
    Instant now = Instant.now();
    var state = new RefreshTokenStore.RefreshTokenState("owned", "owner", "meeting", now.plusSeconds(7200),
        now.plusSeconds(3600), RefreshTokenStore.TokenStatus.ACTIVE);
    store.createIfAbsent(state);
    assertThat(store.revoke("owned", "other")).isFalse();
    assertThat(store.createIfAbsent(state).status()).isEqualTo(RefreshTokenStore.TokenStatus.ACTIVE);
    assertThat(store.revoke("owned", "owner")).isTrue();
    assertThat(store.createIfAbsent(state).status()).isEqualTo(RefreshTokenStore.TokenStatus.REVOKED);
  }

  @Test
  void revokeDoesNotCreateStateForUnknownTokenId() {
    InMemoryRefreshTokenStore store = new InMemoryRefreshTokenStore();

    store.revoke("missing-token-id-1", "user-1");

    RefreshTokenStore.ConsumeResult consumeResult = store.consume("missing-token-id-1");

    assertThat(consumeResult.status()).isEqualTo(RefreshTokenStore.ConsumeStatus.MISSING);
    assertThat(consumeResult.state()).isNull();
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
        .isEqualTo(RefreshTokenStore.ConsumeStatus.REVOKED);
  }
}
