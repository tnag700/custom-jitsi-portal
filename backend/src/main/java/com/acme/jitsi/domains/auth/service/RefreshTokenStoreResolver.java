package com.acme.jitsi.domains.auth.service;

import java.util.Locale;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
class RefreshTokenStoreResolver {

  private final RefreshTokenStore databaseRefreshTokenStore;
  private final RedisRefreshTokenStore redisRefreshTokenStore;

  RefreshTokenStoreResolver(
      @Qualifier("databaseRefreshTokenStore") RefreshTokenStore databaseRefreshTokenStore,
      RedisRefreshTokenStore redisRefreshTokenStore) {
    this.databaseRefreshTokenStore = databaseRefreshTokenStore;
    this.redisRefreshTokenStore = redisRefreshTokenStore;
  }

  RefreshTokenStore resolve(
      AuthRefreshProperties properties,
      ObjectProvider<StringRedisTemplate> redisTemplateProvider) {
    String rawMode = properties.atomicStore();
    String mode = rawMode == null || rawMode.isBlank()
        ? "in-memory"
        : rawMode.trim().toLowerCase(Locale.ROOT);
    return switch (mode) {
      case "database" -> databaseRefreshTokenStore;
      case "redis" -> {
        StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
        if (redisTemplate == null) {
          throw new IllegalStateException(
              "Redis refresh-token store was requested but Redis is unavailable; refusing an in-memory fallback.");
        }
        yield redisRefreshTokenStore;
      }
      case "in-memory", "in_memory" -> new InMemoryRefreshTokenStore();
      default -> throw new IllegalArgumentException("Unsupported refresh-token store mode: " + mode);
    };
  }
}
