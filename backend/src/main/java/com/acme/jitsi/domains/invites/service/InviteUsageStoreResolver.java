package com.acme.jitsi.domains.invites.service;

import com.acme.jitsi.domains.store.StoreMode;
import com.acme.jitsi.domains.store.StoreSelectionStrategy;
import com.acme.jitsi.domains.store.StoreSelectionStrategyFactory;
import org.springframework.stereotype.Component;

@Component
class InviteUsageStoreResolver {

  private final InviteExchangeProperties properties;
  private final InMemoryInviteUsageStore inMemoryInviteUsageStore;
  private final RedisInviteUsageStore redisInviteUsageStore;
  private final StoreSelectionStrategyFactory strategyFactory;

  InviteUsageStoreResolver(
      InviteExchangeProperties properties,
      InMemoryInviteUsageStore inMemoryInviteUsageStore,
      RedisInviteUsageStore redisInviteUsageStore,
      StoreSelectionStrategyFactory strategyFactory) {
    this.properties = properties;
    this.inMemoryInviteUsageStore = inMemoryInviteUsageStore;
    this.redisInviteUsageStore = redisInviteUsageStore;
    this.strategyFactory = strategyFactory;
  }

  InviteUsageStore resolve() {
    StoreSelectionStrategy<InviteUsageStore> strategy = strategyFactory.create(
        StoreMode.REDIS,
        mode -> mode == StoreMode.REDIS ? redisInviteUsageStore : inMemoryInviteUsageStore);

    return strategy.select(properties.atomicStore());
  }
}