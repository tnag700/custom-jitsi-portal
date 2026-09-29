package com.acme.jitsi.domains.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

class RedisRefreshTokenStoreTest {

  @Test
  void unavailableRedisFailsClosedForEveryMutation() {
    RedisRefreshTokenStore store = store(null);
    assertThatThrownBy(() -> store.createIfAbsent(activeState())).isInstanceOf(RetryableRefreshTokenException.class);
    assertThatThrownBy(() -> store.consume("root")).isInstanceOf(RetryableRefreshTokenException.class);
    assertThatThrownBy(() -> store.rotate("root", activeState())).isInstanceOf(RetryableRefreshTokenException.class);
    assertThatThrownBy(() -> store.revoke("root", "owner")).isInstanceOf(RetryableRefreshTokenException.class);
  }

  @Test
  void missingScriptResultCannotPretendCreationSucceeded() {
    RedisRefreshTokenStore store = store(mock(StringRedisTemplate.class));
    assertThatThrownBy(() -> store.createIfAbsent(activeState())).isInstanceOf(AuthTokenException.class);
  }

  @SuppressWarnings("unchecked")
  @Test
  void malformedExistingStateCannotBeRecreatedAsActive() {
    StringRedisTemplate redis = mock(StringRedisTemplate.class);
    HashOperations<String, Object, Object> hashes = mock(HashOperations.class);
    when(redis.opsForHash()).thenReturn(hashes);
    when(redis.execute(any(DefaultRedisScript.class), anyList(), any(Object[].class))).thenReturn("EXISTS");
    when(hashes.entries("auth:refresh:root")).thenReturn(Map.of("status", "ACTIVE"));
    assertThatThrownBy(() -> store(redis).createIfAbsent(activeState())).isInstanceOf(AuthTokenException.class);
  }

  @SuppressWarnings("unchecked")
  @Test
  void malformedExpiryPreservesParsingCause() {
    StringRedisTemplate redis = mock(StringRedisTemplate.class);
    HashOperations<String, Object, Object> hashes = mock(HashOperations.class);
    when(redis.opsForHash()).thenReturn(hashes);
    when(redis.execute(any(DefaultRedisScript.class), anyList(), any(Object[].class))).thenReturn("EXISTS");
    when(hashes.entries("auth:refresh:root")).thenReturn(Map.of(
        "tokenId", "root", "subject", "owner", "meetingId", "meeting",
        "absoluteExpiresAt", "invalid", "idleExpiresAt", "invalid", "status", "ACTIVE"));

    assertThatThrownBy(() -> store(redis).createIfAbsent(activeState()))
        .isInstanceOf(AuthTokenException.class)
        .hasCauseInstanceOf(NumberFormatException.class);
  }

  @SuppressWarnings("unchecked")
  @Test
  void legacyStateWithoutFamilyIsRefused() {
    StringRedisTemplate redis = mock(StringRedisTemplate.class);
    HashOperations<String, Object, Object> hashes = mock(HashOperations.class);
    when(redis.opsForHash()).thenReturn(hashes);
    when(redis.execute(any(DefaultRedisScript.class), anyList(), any(Object[].class))).thenReturn("EXISTS");
    RefreshTokenStore.RefreshTokenState state = activeState();
    when(hashes.entries("auth:refresh:root")).thenReturn(Map.of(
        "tokenId", state.tokenId(), "subject", state.subject(), "meetingId", state.meetingId(),
        "absoluteExpiresAt", Long.toString(state.absoluteExpiresAt().toEpochMilli()),
        "idleExpiresAt", Long.toString(state.idleExpiresAt().toEpochMilli()), "status", "ACTIVE"));
    assertThat(store(redis).createIfAbsent(state).status()).isEqualTo(RefreshTokenStore.TokenStatus.REVOKED);
  }

  @SuppressWarnings("unchecked")
  private RedisRefreshTokenStore store(StringRedisTemplate redis) {
    ObjectProvider<StringRedisTemplate> provider = mock(ObjectProvider.class);
    when(provider.getIfAvailable()).thenReturn(redis);
    return new RedisRefreshTokenStore(provider);
  }

  private RefreshTokenStore.RefreshTokenState activeState() {
    Instant now = Instant.now();
    return new RefreshTokenStore.RefreshTokenState("root", "owner", "meeting", now.plusSeconds(3600),
        now.plusSeconds(1800), RefreshTokenStore.TokenStatus.ACTIVE);
  }
}
