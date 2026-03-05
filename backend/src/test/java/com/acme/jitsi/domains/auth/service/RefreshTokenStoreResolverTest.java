package com.acme.jitsi.domains.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.acme.jitsi.domains.store.StoreSelectionStrategyFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;

class RefreshTokenStoreResolverTest {

  @Test
  void resolvesRedisStoreWhenModeIsRedisAndTemplateIsAvailable() {
    AuthRefreshProperties properties = new AuthRefreshProperties();
    properties.setAtomicStore("redis");

    ObjectProvider<StringRedisTemplate> provider = mockProvider(mock(StringRedisTemplate.class));
    RedisRefreshTokenStore redisStore = new RedisRefreshTokenStore(provider);
    RefreshTokenStoreResolver resolver = new RefreshTokenStoreResolver(new StoreSelectionStrategyFactory(), redisStore);

    assertThat(resolver.resolve(properties, provider)).isInstanceOf(RedisRefreshTokenStore.class);
  }

  @Test
  void fallsBackToInMemoryWhenModeIsRedisAndTemplateIsMissing() {
    AuthRefreshProperties properties = new AuthRefreshProperties();
    properties.setAtomicStore("redis");

    ObjectProvider<StringRedisTemplate> provider = mockProvider(null);
    RedisRefreshTokenStore redisStore = new RedisRefreshTokenStore(provider);
    RefreshTokenStoreResolver resolver = new RefreshTokenStoreResolver(new StoreSelectionStrategyFactory(), redisStore);

    assertThat(resolver.resolve(properties, provider)).isInstanceOf(InMemoryRefreshTokenStore.class);
  }

  @Test
  void resolvesInMemoryWhenModeIsInMemory() {
    AuthRefreshProperties properties = new AuthRefreshProperties();
    properties.setAtomicStore("in-memory");

    ObjectProvider<StringRedisTemplate> provider = mockProvider(mock(StringRedisTemplate.class));
    RedisRefreshTokenStore redisStore = new RedisRefreshTokenStore(provider);
    RefreshTokenStoreResolver resolver = new RefreshTokenStoreResolver(new StoreSelectionStrategyFactory(), redisStore);

    assertThat(resolver.resolve(properties, provider))
        .isInstanceOf(InMemoryRefreshTokenStore.class);
  }

  @Test
  void defaultsToInMemoryWhenModeIsNullOrBlank() {
    AuthRefreshProperties nullModeProperties = new AuthRefreshProperties();
    nullModeProperties.setAtomicStore(null);

    AuthRefreshProperties blankModeProperties = new AuthRefreshProperties();
    blankModeProperties.setAtomicStore("   ");

    ObjectProvider<StringRedisTemplate> provider = mockProvider(mock(StringRedisTemplate.class));
    RedisRefreshTokenStore redisStore = new RedisRefreshTokenStore(provider);
    RefreshTokenStoreResolver resolver = new RefreshTokenStoreResolver(new StoreSelectionStrategyFactory(), redisStore);

    assertThat(resolver.resolve(nullModeProperties, provider))
        .isInstanceOf(InMemoryRefreshTokenStore.class);
    assertThat(resolver.resolve(blankModeProperties, provider))
        .isInstanceOf(InMemoryRefreshTokenStore.class);
  }

  @Test
  void fallsBackToInMemoryWhenModeIsUnknown() {
    AuthRefreshProperties properties = new AuthRefreshProperties();
    properties.setAtomicStore("unexpected");

    ObjectProvider<StringRedisTemplate> provider = mockProvider(mock(StringRedisTemplate.class));
    RedisRefreshTokenStore redisStore = new RedisRefreshTokenStore(provider);
    RefreshTokenStoreResolver resolver = new RefreshTokenStoreResolver(new StoreSelectionStrategyFactory(), redisStore);

    assertThat(resolver.resolve(properties, provider))
        .isInstanceOf(InMemoryRefreshTokenStore.class);
  }

  @SuppressWarnings("unchecked")
  private ObjectProvider<StringRedisTemplate> mockProvider(StringRedisTemplate template) {
    ObjectProvider<StringRedisTemplate> provider = mock(ObjectProvider.class);
    when(provider.getIfAvailable()).thenReturn(template);
    return provider;
  }
}
