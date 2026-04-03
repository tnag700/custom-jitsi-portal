package com.acme.jitsi.domains.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.acme.jitsi.domains.store.StoreSelectionStrategyFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;

class RefreshTokenStoreSelectionIntegrationTest {

  @Test
  void selectsInMemoryStoreWhenModeIsInMemory() {
    AuthRefreshProperties properties = new AuthRefreshProperties();
    properties.setAtomicStore("in-memory");

    ObjectProvider<StringRedisTemplate> provider = redisProvider(null);
    RefreshTokenStoreResolver resolver = new RefreshTokenStoreResolver(new StoreSelectionStrategyFactory(), new RedisRefreshTokenStore(provider));

    RefreshTokenStore store = RefreshTokenStoreConfiguration.refreshTokenStore(
        properties,
        resolver,
        provider);

    assertThat(store).isInstanceOf(InMemoryRefreshTokenStore.class);
  }

  @Test
  void fallsBackToInMemoryWhenRedisModeAndRedisIsUnavailable() {
    AuthRefreshProperties properties = new AuthRefreshProperties();
    properties.setAtomicStore("redis");

    ObjectProvider<StringRedisTemplate> provider = redisProvider(null);
    RefreshTokenStoreResolver resolver = new RefreshTokenStoreResolver(new StoreSelectionStrategyFactory(), new RedisRefreshTokenStore(provider));

    RefreshTokenStore store = RefreshTokenStoreConfiguration.refreshTokenStore(
        properties,
        resolver,
        provider);

    assertThat(store).isInstanceOf(InMemoryRefreshTokenStore.class);
  }

  @Test
  void selectsRedisStoreWhenRedisModeAndRedisIsAvailable() {
    AuthRefreshProperties properties = new AuthRefreshProperties();
    properties.setAtomicStore("redis");

    ObjectProvider<StringRedisTemplate> provider = redisProvider(mock(StringRedisTemplate.class));
    RefreshTokenStoreResolver resolver = new RefreshTokenStoreResolver(new StoreSelectionStrategyFactory(), new RedisRefreshTokenStore(provider));

    RefreshTokenStore store = RefreshTokenStoreConfiguration.refreshTokenStore(
        properties,
        resolver,
        provider);

    assertThat(store).isInstanceOf(RedisRefreshTokenStore.class);
  }

  @SuppressWarnings("unchecked")
  private ObjectProvider<StringRedisTemplate> redisProvider(StringRedisTemplate template) {
    ObjectProvider<StringRedisTemplate> provider = mock(ObjectProvider.class);
    when(provider.getIfAvailable()).thenReturn(template);
    return provider;
  }
}