package com.acme.jitsi.domains.auth.service;

import com.acme.jitsi.shared.ErrorCode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.resilience.annotation.ConcurrencyLimit;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Component;

@Component
@Retryable(
  includes = RetryableRefreshTokenException.class,
  maxRetriesString = "${app.resilience.auth-refresh.redis.retry.max-retries:2}",
  delayString = "${app.resilience.auth-refresh.redis.retry.delay:100ms}",
  maxDelayString = "${app.resilience.auth-refresh.redis.retry.max-delay:1s}")
@ConcurrencyLimit(limitString = "${app.resilience.auth-refresh.redis.concurrency-limit:32}")
class RedisRefreshTokenStore implements RefreshTokenStore {

  private static final String FIELD_TOKEN_ID = "tokenId";
  private static final String FIELD_SUBJECT = "subject";
  private static final String FIELD_MEETING_ID = "meetingId";
  private static final String FIELD_ABSOLUTE_EXPIRES_AT = "absoluteExpiresAt";
  private static final String FIELD_IDLE_EXPIRES_AT = "idleExpiresAt";
  private static final String FIELD_STATUS = "status";

  private static final DefaultRedisScript<String> CONSUME_SCRIPT = new DefaultRedisScript<>(
      "local key = KEYS[1] "
          + "if redis.call('EXISTS', key) == 0 then return 'MISSING' end "
          + "local status = redis.call('HGET', key, 'status') "
          + "if status == 'REVOKED' then return 'REVOKED' end "
          + "if status == 'USED' then return 'USED' end "
          + "redis.call('HSET', key, 'status', 'USED') "
          + "return 'CONSUMED'",
      String.class);

        private static final DefaultRedisScript<String> CREATE_IF_ABSENT_SCRIPT = new DefaultRedisScript<>(
          "local key = KEYS[1] "
            + "if redis.call('EXISTS', key) == 1 then return 'EXISTS' end "
            + "redis.call('HSET', key, "
            + "'tokenId', ARGV[1], "
            + "'subject', ARGV[2], "
            + "'meetingId', ARGV[3], "
            + "'absoluteExpiresAt', ARGV[4], "
            + "'idleExpiresAt', ARGV[5], "
            + "'status', ARGV[6]) "
            + "redis.call('PEXPIREAT', key, ARGV[7]) "
            + "return 'CREATED'",
          String.class);

  private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;

  RedisRefreshTokenStore(ObjectProvider<StringRedisTemplate> redisTemplateProvider) {
    this.redisTemplateProvider = redisTemplateProvider;
  }

  @Override
  public RefreshTokenState createIfAbsent(RefreshTokenState state) {
    StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
    if (redisTemplate == null) {
      return state; // Fallback behavior if Redis is unavailable
    }

    try {
      String key = key(state.tokenId());
      String result = redisTemplate.execute(
          CREATE_IF_ABSENT_SCRIPT,
          List.of(key),
          state.tokenId(),
          state.subject(),
          state.meetingId(),
          Long.toString(state.absoluteExpiresAt().toEpochMilli()),
          Long.toString(state.idleExpiresAt().toEpochMilli()),
          state.status().name(),
          Long.toString(state.absoluteExpiresAt().toEpochMilli()));

      if ("CREATED".equals(result)) {
        return state;
      }

      RefreshTokenState existingState = loadState(key);
      return existingState == null ? state : existingState;
    } catch (DataAccessException ex) {
      throw new RetryableRefreshTokenException(
          org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
          ErrorCode.CONFIG_INCOMPATIBLE.code(),
          "Redis недоступен для атомарного учета токенов.",
          ex);
    }
  }

  @Override
  public ConsumeResult consume(String tokenId) {
    StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
    if (redisTemplate == null) {
      return new ConsumeResult(ConsumeStatus.MISSING, null);
    }

    try {
      String key = key(tokenId);
      String result = redisTemplate.execute(CONSUME_SCRIPT, List.of(key));
      ConsumeStatus status = switch (result) {
        case "CONSUMED" -> ConsumeStatus.CONSUMED;
        case "USED" -> ConsumeStatus.USED;
        case "REVOKED" -> ConsumeStatus.REVOKED;
        default -> ConsumeStatus.MISSING;
      };

      return new ConsumeResult(status, loadState(key));
    } catch (DataAccessException ex) {
      throw new RetryableRefreshTokenException(
          org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
          ErrorCode.CONFIG_INCOMPATIBLE.code(),
          "Redis недоступен для атомарного учета токенов.",
          ex);
    }
  }

  @Override
  public void create(RefreshTokenState state) {
    try {
      saveState(key(state.tokenId()), state);
    } catch (DataAccessException ex) {
      throw new RetryableRefreshTokenException(
          org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
          ErrorCode.CONFIG_INCOMPATIBLE.code(),
          "Redis недоступен для атомарного учета токенов.",
          ex);
    }
  }

  @Override
  public void revoke(String tokenId) {
    StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
    if (redisTemplate == null) {
      return;
    }

    try {
      String key = key(tokenId);
      if (Boolean.TRUE.equals(redisTemplate.hasKey(key))) {
        redisTemplate.opsForHash().put(key, FIELD_STATUS, TokenStatus.REVOKED.name());
        return;
      }

      Instant placeholderExpiry = Instant.now().plus(30, ChronoUnit.DAYS);
      saveState(key, new RefreshTokenState(
          tokenId,
          "",
          "",
          placeholderExpiry,
          placeholderExpiry,
          TokenStatus.REVOKED));
    } catch (DataAccessException ex) {
      throw new RetryableRefreshTokenException(
          org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
          ErrorCode.CONFIG_INCOMPATIBLE.code(),
          "Redis недоступен для атомарного учета токенов.",
          ex);
    }
  }

  private void saveState(String key, RefreshTokenState state) {
    StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
    if (redisTemplate == null) {
      return;
    }

    redisTemplate.opsForHash().putAll(key, Map.of(
        FIELD_TOKEN_ID, state.tokenId(),
        FIELD_SUBJECT, state.subject(),
        FIELD_MEETING_ID, state.meetingId(),
        FIELD_ABSOLUTE_EXPIRES_AT, Long.toString(state.absoluteExpiresAt().toEpochMilli()),
        FIELD_IDLE_EXPIRES_AT, Long.toString(state.idleExpiresAt().toEpochMilli()),
        FIELD_STATUS, state.status().name()));
    redisTemplate.expireAt(key, Date.from(state.absoluteExpiresAt()));
  }

  private RefreshTokenState loadState(String key) {
    StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
    if (redisTemplate == null) {
      return null;
    }

    Map<Object, Object> map = redisTemplate.opsForHash().entries(key);
    if (map == null || map.isEmpty()) {
      return null;
    }

    try {
      return new RefreshTokenState(
          stringValue(map.get(FIELD_TOKEN_ID)),
          stringValue(map.get(FIELD_SUBJECT)),
          stringValue(map.get(FIELD_MEETING_ID)),
          Instant.ofEpochMilli(Long.parseLong(stringValue(map.get(FIELD_ABSOLUTE_EXPIRES_AT)))),
          Instant.ofEpochMilli(Long.parseLong(stringValue(map.get(FIELD_IDLE_EXPIRES_AT)))),
          TokenStatus.valueOf(stringValue(map.get(FIELD_STATUS))));
    } catch (RuntimeException ex) {
      return null;
    }
  }

  private String stringValue(Object value) {
    return value == null ? "" : value.toString();
  }

  private String key(String tokenId) {
    return "auth:refresh:" + tokenId;
  }
}
