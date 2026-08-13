package com.acme.jitsi.domains.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;

class RefreshTokenStoreSelectionIntegrationTest {

  @Test
  void selectsInMemoryStoreWhenModeIsInMemory() {
    AuthRefreshProperties properties = new AuthRefreshProperties();
    properties.setAtomicStore("in-memory");

    ObjectProvider<StringRedisTemplate> provider = redisProvider(null);
    RefreshTokenStoreResolver resolver = resolver(provider);

    RefreshTokenStore store = RefreshTokenStoreConfiguration.refreshTokenStore(
        properties,
        resolver,
        provider);

    assertThat(store).isInstanceOf(InMemoryRefreshTokenStore.class);
  }

  @Test
  void failsClosedWhenRedisModeAndRedisIsUnavailable() {
    AuthRefreshProperties properties = new AuthRefreshProperties();
    properties.setAtomicStore("redis");

    ObjectProvider<StringRedisTemplate> provider = redisProvider(null);
    RefreshTokenStoreResolver resolver = resolver(provider);

    org.assertj.core.api.Assertions.assertThatThrownBy(() -> RefreshTokenStoreConfiguration.refreshTokenStore(
            properties,
            resolver,
            provider))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void selectsRedisStoreWhenRedisModeAndRedisIsAvailable() {
    AuthRefreshProperties properties = new AuthRefreshProperties();
    properties.setAtomicStore("redis");

    ObjectProvider<StringRedisTemplate> provider = redisProvider(mock(StringRedisTemplate.class));
    RefreshTokenStoreResolver resolver = resolver(provider);

    RefreshTokenStore store = RefreshTokenStoreConfiguration.refreshTokenStore(
        properties,
        resolver,
        provider);

    assertThat(store).isInstanceOf(RedisRefreshTokenStore.class);
  }

  @Test
  void selectsDatabaseStoreWhenDatabaseModeIsConfigured() {
    AuthRefreshProperties properties = new AuthRefreshProperties();
    properties.setAtomicStore("database");
    ObjectProvider<StringRedisTemplate> provider = redisProvider(null);
    RefreshTokenStore databaseStore = mock(RefreshTokenStore.class);
    RefreshTokenStoreResolver resolver = new RefreshTokenStoreResolver(databaseStore, new RedisRefreshTokenStore(provider));

    assertThat(RefreshTokenStoreConfiguration.refreshTokenStore(properties, resolver, provider))
        .isSameAs(databaseStore);
  }

  private RefreshTokenStoreResolver resolver(ObjectProvider<StringRedisTemplate> provider) {
    return new RefreshTokenStoreResolver(
        mock(RefreshTokenStore.class),
        new RedisRefreshTokenStore(provider));
  }

  @SuppressWarnings("unchecked")
  private ObjectProvider<StringRedisTemplate> redisProvider(StringRedisTemplate template) {
    ObjectProvider<StringRedisTemplate> provider = mock(ObjectProvider.class);
    when(provider.getIfAvailable()).thenReturn(template);
    return provider;
  }
}
