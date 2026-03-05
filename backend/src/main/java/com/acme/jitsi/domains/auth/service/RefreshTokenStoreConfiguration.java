package com.acme.jitsi.domains.auth.service;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
class RefreshTokenStoreConfiguration {

  @Bean
  @Primary
  RefreshTokenStore refreshTokenStore(
      AuthRefreshProperties properties,
      RefreshTokenStoreResolver refreshTokenStoreResolver,
      ObjectProvider<StringRedisTemplate> redisTemplateProvider) {
    return refreshTokenStoreResolver.resolve(properties, redisTemplateProvider);
  }
}
