package com.acme.jitsi.domains.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
    RefreshTokenStoreResolver resolver = resolver(redisStore);

    assertThat(resolver.resolve(properties, provider)).isInstanceOf(RedisRefreshTokenStore.class);
  }

  @Test
  void failsClosedWhenModeIsRedisAndTemplateIsMissing() {
    AuthRefreshProperties properties = new AuthRefreshProperties();
    properties.setAtomicStore("redis");

    ObjectProvider<StringRedisTemplate> provider = mockProvider(null);
    RedisRefreshTokenStore redisStore = new RedisRefreshTokenStore(provider);
    RefreshTokenStoreResolver resolver = resolver(redisStore);

    assertThatThrownBy(() -> resolver.resolve(properties, provider))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Redis");
  }

  @Test
  void resolvesInMemoryWhenModeIsInMemory() {
    AuthRefreshProperties properties = new AuthRefreshProperties();
    properties.setAtomicStore("in-memory");

    ObjectProvider<StringRedisTemplate> provider = mockProvider(mock(StringRedisTemplate.class));
    RedisRefreshTokenStore redisStore = new RedisRefreshTokenStore(provider);
    RefreshTokenStoreResolver resolver = resolver(redisStore);

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
    RefreshTokenStoreResolver resolver = resolver(redisStore);

    assertThat(resolver.resolve(nullModeProperties, provider))
        .isInstanceOf(InMemoryRefreshTokenStore.class);
    assertThat(resolver.resolve(blankModeProperties, provider))
        .isInstanceOf(InMemoryRefreshTokenStore.class);
  }

  @Test
  void failsClosedWhenModeIsUnknown() {
    AuthRefreshProperties properties = new AuthRefreshProperties();
    properties.setAtomicStore("unexpected");

    ObjectProvider<StringRedisTemplate> provider = mockProvider(mock(StringRedisTemplate.class));
    RedisRefreshTokenStore redisStore = new RedisRefreshTokenStore(provider);
    RefreshTokenStoreResolver resolver = resolver(redisStore);

    assertThatThrownBy(() -> resolver.resolve(properties, provider))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("unexpected");
  }

  @Test
  void resolvesDatabaseStoreWithoutConsultingRedis() {
    AuthRefreshProperties properties = new AuthRefreshProperties();
    properties.setAtomicStore("database");
    ObjectProvider<StringRedisTemplate> provider = mockProvider(null);
    RefreshTokenStore databaseStore = mock(RefreshTokenStore.class);
    RefreshTokenStoreResolver resolver = new RefreshTokenStoreResolver(databaseStore, new RedisRefreshTokenStore(provider));

    assertThat(resolver.resolve(properties, provider)).isSameAs(databaseStore);
  }

  private RefreshTokenStoreResolver resolver(RedisRefreshTokenStore redisStore) {
    return new RefreshTokenStoreResolver(mock(RefreshTokenStore.class), redisStore);
  }

  @SuppressWarnings("unchecked")
  private ObjectProvider<StringRedisTemplate> mockProvider(StringRedisTemplate template) {
    ObjectProvider<StringRedisTemplate> provider = mock(ObjectProvider.class);
    when(provider.getIfAvailable()).thenReturn(template);
    return provider;
  }
}
