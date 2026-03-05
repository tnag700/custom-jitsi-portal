package com.acme.jitsi.domains.auth.service;

import com.acme.jitsi.domains.store.StoreMode;
import com.acme.jitsi.domains.store.StoreSelectionStrategy;
import com.acme.jitsi.domains.store.StoreSelectionStrategyFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
class RefreshTokenStoreResolver {

  private final StoreSelectionStrategyFactory strategyFactory;
  private final RedisRefreshTokenStore redisRefreshTokenStore;

  RefreshTokenStoreResolver(
      StoreSelectionStrategyFactory strategyFactory,
      RedisRefreshTokenStore redisRefreshTokenStore) {
    this.strategyFactory = strategyFactory;
    this.redisRefreshTokenStore = redisRefreshTokenStore;
  }

  RefreshTokenStore resolve(
      AuthRefreshProperties properties,
      ObjectProvider<StringRedisTemplate> redisTemplateProvider) {
    StoreSelectionStrategy<RefreshTokenStore> strategy = strategyFactory.createWithFallback(
        StoreMode.IN_MEMORY,
        StoreMode.IN_MEMORY,
        mode -> {
          if (mode == StoreMode.REDIS) {
            StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
            return redisTemplate == null ? null : redisRefreshTokenStore;
          }
          return new InMemoryRefreshTokenStore();
        });

    return strategy.select(properties.atomicStore());
  }
}