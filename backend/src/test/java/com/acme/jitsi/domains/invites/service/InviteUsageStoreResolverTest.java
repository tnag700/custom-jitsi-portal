package com.acme.jitsi.domains.invites.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.acme.jitsi.domains.store.StoreSelectionStrategyFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class InviteUsageStoreResolverTest {

  @Test
  void resolvesRedisStoreWhenModeIsRedis() {
    InviteExchangeProperties properties = new InviteExchangeProperties();
    properties.setAtomicStore("redis");

    InviteUsageStoreResolver resolver = new InviteUsageStoreResolver(
        properties,
        new InMemoryInviteUsageStore(),
      new RedisInviteUsageStore(mockRedisProvider()),
      new StoreSelectionStrategyFactory());

    assertThat(resolver.resolve()).isInstanceOf(RedisInviteUsageStore.class);
  }

  @Test
  void resolvesInMemoryStoreWhenModeIsInMemory() {
    InviteExchangeProperties properties = new InviteExchangeProperties();
    properties.setAtomicStore("in-memory");

    InviteUsageStoreResolver resolver = new InviteUsageStoreResolver(
        properties,
        new InMemoryInviteUsageStore(),
      new RedisInviteUsageStore(mockRedisProvider()),
      new StoreSelectionStrategyFactory());

    assertThat(resolver.resolve()).isInstanceOf(InMemoryInviteUsageStore.class);
  }

  @Test
  void defaultsToRedisStoreWhenModeIsNullOrBlank() {
    InviteExchangeProperties nullModeProperties = new InviteExchangeProperties();
    nullModeProperties.setAtomicStore(null);

    InviteUsageStoreResolver nullModeResolver = new InviteUsageStoreResolver(
        nullModeProperties,
        new InMemoryInviteUsageStore(),
      new RedisInviteUsageStore(mockRedisProvider()),
      new StoreSelectionStrategyFactory());

    InviteExchangeProperties blankModeProperties = new InviteExchangeProperties();
    blankModeProperties.setAtomicStore("   ");

    InviteUsageStoreResolver blankModeResolver = new InviteUsageStoreResolver(
        blankModeProperties,
        new InMemoryInviteUsageStore(),
      new RedisInviteUsageStore(mockRedisProvider()),
      new StoreSelectionStrategyFactory());

    assertThat(nullModeResolver.resolve()).isInstanceOf(RedisInviteUsageStore.class);
    assertThat(blankModeResolver.resolve()).isInstanceOf(RedisInviteUsageStore.class);
  }

  @Test
  void fallsBackToInMemoryStoreWhenModeIsUnknown() {
    InviteExchangeProperties properties = new InviteExchangeProperties();
    properties.setAtomicStore("unknown-mode");

    InviteUsageStoreResolver resolver = new InviteUsageStoreResolver(
        properties,
        new InMemoryInviteUsageStore(),
      new RedisInviteUsageStore(mockRedisProvider()),
      new StoreSelectionStrategyFactory());

    assertThat(resolver.resolve()).isInstanceOf(InMemoryInviteUsageStore.class);
  }

  @SuppressWarnings("unchecked")
  private ObjectProvider<org.springframework.data.redis.core.StringRedisTemplate> mockRedisProvider() {
    return mock(ObjectProvider.class);
  }
}
