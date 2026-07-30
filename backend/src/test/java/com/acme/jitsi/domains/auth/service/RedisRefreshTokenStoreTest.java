package com.acme.jitsi.domains.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.StringRedisTemplate;

class RedisRefreshTokenStoreTest {

  @Test
  void revokeCreatesRevokedMarkerWhenTokenStateIsMissing() {
    StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    HashOperations<String, Object, Object> hashOperations = mock(HashOperations.class);
    when(redisTemplate.opsForHash()).thenReturn(hashOperations);
    when(redisTemplate.hasKey("auth:refresh:missing-token-id-1")).thenReturn(false);

    @SuppressWarnings("unchecked")
    org.springframework.beans.factory.ObjectProvider<StringRedisTemplate> provider = mock(org.springframework.beans.factory.ObjectProvider.class);
    when(provider.getIfAvailable()).thenReturn(redisTemplate);
    RedisRefreshTokenStore store = new RedisRefreshTokenStore(provider);

    store.revoke("missing-token-id-1");

    verify(hashOperations).putAll(eq("auth:refresh:missing-token-id-1"), org.mockito.ArgumentMatchers.anyMap());
    verify(redisTemplate).expireAt(eq("auth:refresh:missing-token-id-1"), org.mockito.ArgumentMatchers.any(java.util.Date.class));
  }

  @SuppressWarnings("unchecked")
  @Test
  void createIfAbsentUsesAtomicLuaScriptInRedis() {
    StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);

    when(redisTemplate.execute(
        org.mockito.ArgumentMatchers.any(DefaultRedisScript.class),
        eq(List.of("auth:refresh:token-1")),
        eq("token-1"),
        eq("user-1"),
        eq("meeting-1"),
        org.mockito.ArgumentMatchers.anyString(),
        org.mockito.ArgumentMatchers.anyString(),
        eq("ACTIVE"),
        org.mockito.ArgumentMatchers.anyString()))
        .thenReturn("CREATED");

    org.springframework.beans.factory.ObjectProvider<StringRedisTemplate> provider = mock(org.springframework.beans.factory.ObjectProvider.class);
    when(provider.getIfAvailable()).thenReturn(redisTemplate);
    RedisRefreshTokenStore store = new RedisRefreshTokenStore(provider);
    Instant absolute = Instant.now().plus(2, ChronoUnit.HOURS);
    Instant idle = Instant.now().plus(1, ChronoUnit.HOURS);
    store.createIfAbsent(new RefreshTokenStore.RefreshTokenState(
        "token-1",
        "user-1",
        "meeting-1",
        absolute,
        idle,
        RefreshTokenStore.TokenStatus.ACTIVE));

    verify(redisTemplate, atLeastOnce()).execute(
        org.mockito.ArgumentMatchers.any(DefaultRedisScript.class),
        eq(List.of("auth:refresh:token-1")),
        eq("token-1"),
        eq("user-1"),
        eq("meeting-1"),
        org.mockito.ArgumentMatchers.anyString(),
        org.mockito.ArgumentMatchers.anyString(),
        eq("ACTIVE"),
        org.mockito.ArgumentMatchers.anyString());
  }

  @SuppressWarnings("unchecked")
  @Test
  void rotateUsesOneLuaScriptForConsumeAndSuccessorCreation() {
    StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    HashOperations<String, Object, Object> hashOperations = mock(HashOperations.class);
    when(redisTemplate.opsForHash()).thenReturn(hashOperations);
    when(hashOperations.entries("auth:refresh:current-token")).thenReturn(Map.of(
        "tokenId", "current-token",
        "subject", "user-1",
        "meetingId", "meeting-1",
        "absoluteExpiresAt", "1893456000000",
        "idleExpiresAt", "1893452400000",
        "status", "USED"));
    when(redisTemplate.execute(
        org.mockito.ArgumentMatchers.any(DefaultRedisScript.class),
        eq(List.of("auth:refresh:current-token", "auth:refresh:successor-token")),
        eq("successor-token"),
        eq("user-1"),
        eq("meeting-1"),
        org.mockito.ArgumentMatchers.anyString(),
        org.mockito.ArgumentMatchers.anyString(),
        eq("ACTIVE"),
        org.mockito.ArgumentMatchers.anyString()))
        .thenReturn("CONSUMED");

    org.springframework.beans.factory.ObjectProvider<StringRedisTemplate> provider =
        mock(org.springframework.beans.factory.ObjectProvider.class);
    when(provider.getIfAvailable()).thenReturn(redisTemplate);
    RedisRefreshTokenStore store = new RedisRefreshTokenStore(provider);
    Instant absolute = Instant.now().plus(2, ChronoUnit.HOURS);
    Instant idle = Instant.now().plus(1, ChronoUnit.HOURS);
    RefreshTokenStore.ConsumeResult result = store.rotate(
        "current-token",
        new RefreshTokenStore.RefreshTokenState(
            "successor-token",
            "user-1",
            "meeting-1",
            absolute,
            idle,
            RefreshTokenStore.TokenStatus.ACTIVE));

    assertThat(result.status()).isEqualTo(RefreshTokenStore.ConsumeStatus.CONSUMED);
    verify(redisTemplate).execute(
        org.mockito.ArgumentMatchers.any(DefaultRedisScript.class),
        eq(List.of("auth:refresh:current-token", "auth:refresh:successor-token")),
        eq("successor-token"),
        eq("user-1"),
        eq("meeting-1"),
        org.mockito.ArgumentMatchers.anyString(),
        org.mockito.ArgumentMatchers.anyString(),
        eq("ACTIVE"),
        org.mockito.ArgumentMatchers.anyString());
  }
}
