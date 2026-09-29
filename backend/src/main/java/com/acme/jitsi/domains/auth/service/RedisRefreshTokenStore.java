package com.acme.jitsi.domains.auth.service;

import com.acme.jitsi.shared.ErrorCode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
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
  private static final String FIELD_FAMILY_ID = "familyId";

  // The first token is also the family marker, retained until absolute expiry.
  // Check reuse before idle expiry so an old ancestor still revokes its descendants.
  private static final String VALIDATE_SCRIPT = """
      local key = KEYS[1]
      if redis.call('EXISTS', key) == 0 then return 'MISSING' end
      local familyId = redis.call('HGET', key, 'familyId')
      if not familyId or familyId == '' then return 'REVOKED' end
      local rootKey = 'auth:refresh:' .. familyId
      if redis.call('HGET', rootKey, 'familyId') ~= familyId then return 'REVOKED' end
      local rootStatus = redis.call('HGET', rootKey, 'status')
      local status = redis.call('HGET', key, 'status')
      if rootStatus ~= 'ACTIVE' and rootStatus ~= 'USED' then return 'REVOKED' end
      if status == 'REVOKED' then return 'REVOKED' end
      if status == 'USED' then
        redis.call('HSET', rootKey, 'status', 'REVOKED')
        return 'USED'
      end
      if status ~= 'ACTIVE' then return 'REVOKED' end
      local absolute = tonumber(redis.call('HGET', key, 'absoluteExpiresAt'))
      local rootAbsolute = tonumber(redis.call('HGET', rootKey, 'absoluteExpiresAt'))
      local idle = tonumber(redis.call('HGET', key, 'idleExpiresAt'))
      if not absolute or not rootAbsolute or not idle then return 'REVOKED' end
      absolute = math.min(absolute, rootAbsolute)
      local time = redis.call('TIME')
      local now = tonumber(time[1]) * 1000 + math.floor(tonumber(time[2]) / 1000)
      if absolute <= now or idle <= now then return 'MISSING' end
      """;

  private static final DefaultRedisScript<String> CONSUME_SCRIPT = new DefaultRedisScript<>(
      VALIDATE_SCRIPT + """
      redis.call('HSET', key, 'status', 'USED')
      return 'CONSUMED'
      """, String.class);

  private static final DefaultRedisScript<String> ROTATE_SCRIPT = new DefaultRedisScript<>(
      VALIDATE_SCRIPT + """
      local nextKey = KEYS[2]
      if redis.call('EXISTS', nextKey) == 1 then return 'COLLISION' end
      if redis.call('HGET', key, 'subject') ~= ARGV[2]
          or redis.call('HGET', key, 'meetingId') ~= ARGV[3] then return 'MISMATCH' end
      local nextIdle = tonumber(ARGV[5])
      if not nextIdle then return 'REVOKED' end
      nextIdle = math.min(nextIdle, absolute)
      if nextIdle <= now then return 'MISSING' end
      redis.call('HSET', nextKey,
        'tokenId', ARGV[1], 'subject', ARGV[2], 'meetingId', ARGV[3],
        'absoluteExpiresAt', string.format('%.0f', absolute),
        'idleExpiresAt', string.format('%.0f', nextIdle), 'status', 'ACTIVE', 'familyId', familyId)
      redis.call('PEXPIREAT', nextKey, string.format('%.0f', absolute))
      redis.call('HSET', key, 'status', 'USED')
      return 'CONSUMED'
      """, String.class);

  private static final DefaultRedisScript<String> CREATE_IF_ABSENT_SCRIPT = new DefaultRedisScript<>("""
      local key = KEYS[1]
      if redis.call('EXISTS', key) == 1 then return 'EXISTS' end
      local time = redis.call('TIME')
      local now = tonumber(time[1]) * 1000 + math.floor(tonumber(time[2]) / 1000)
      if tonumber(ARGV[4]) <= now or tonumber(ARGV[5]) <= now then return 'EXPIRED' end
      redis.call('HSET', key,
        'tokenId', ARGV[1], 'subject', ARGV[2], 'meetingId', ARGV[3],
        'absoluteExpiresAt', ARGV[4], 'idleExpiresAt', ARGV[5], 'status', ARGV[6], 'familyId', ARGV[1])
      redis.call('PEXPIREAT', key, ARGV[4])
      return 'CREATED'
      """, String.class);

  private static final DefaultRedisScript<String> REVOKE_SCRIPT = new DefaultRedisScript<>("""
      local key = KEYS[1]
      if redis.call('HGET', key, 'subject') ~= ARGV[1] then return 'MISSING' end
      local familyId = redis.call('HGET', key, 'familyId')
      if not familyId or familyId == '' then return 'MISSING' end
      local rootKey = 'auth:refresh:' .. familyId
      if redis.call('HGET', rootKey, 'familyId') ~= familyId then return 'MISSING' end
      redis.call('HSET', rootKey, 'status', 'REVOKED')
      return 'REVOKED'
      """, String.class);

  private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;

  RedisRefreshTokenStore(ObjectProvider<StringRedisTemplate> redisTemplateProvider) {
    this.redisTemplateProvider = redisTemplateProvider;
  }

  @Override
  public RefreshTokenState createIfAbsent(RefreshTokenState state) {
    StringRedisTemplate redisTemplate = requireRedisTemplate();
    try {
      String key = key(state.tokenId());
      String result = redisTemplate.execute(CREATE_IF_ABSENT_SCRIPT, List.of(key), stateArguments(state));
      if ("CREATED".equals(result) || "EXPIRED".equals(result)) {
        return state;
      }
      if (!"EXISTS".equals(result)) {
        throw invalidState();
      }
      RefreshTokenState existingState = loadState(redisTemplate, key);
      if (existingState == null) {
        throw invalidState();
      }
      return existingState;
    } catch (DataAccessException ex) {
      throw redisUnavailable(ex);
    }
  }

  @Override
  public ConsumeResult consume(String tokenId) {
    StringRedisTemplate redisTemplate = requireRedisTemplate();
    try {
      String key = key(tokenId);
      String result = redisTemplate.execute(CONSUME_SCRIPT, List.of(key));
      return new ConsumeResult(consumeStatus(result), loadState(redisTemplate, key));
    } catch (DataAccessException ex) {
      throw redisUnavailable(ex);
    }
  }

  @Override
  public ConsumeResult rotate(String tokenId, RefreshTokenState nextState) {
    StringRedisTemplate redisTemplate = requireRedisTemplate();
    try {
      String currentKey = key(tokenId);
      String result = redisTemplate.execute(ROTATE_SCRIPT,
          List.of(currentKey, key(nextState.tokenId())), stateArguments(nextState));
      if ("MISMATCH".equals(result)) {
        throw new IllegalArgumentException("A refresh successor must belong to the same session.");
      }
      if ("COLLISION".equals(result)) {
        throw new IllegalStateException("A refresh successor token ID already exists.");
      }
      return new ConsumeResult(consumeStatus(result), loadState(redisTemplate, currentKey));
    } catch (DataAccessException ex) {
      throw redisUnavailable(ex);
    }
  }

  @Override
  public boolean revoke(String tokenId, String subject) {
    StringRedisTemplate redisTemplate = requireRedisTemplate();
    try {
      String result = redisTemplate.execute(REVOKE_SCRIPT, List.of(key(tokenId)), subject);
      if ("REVOKED".equals(result)) {
        return true;
      }
      if ("MISSING".equals(result)) {
        return false;
      }
      throw invalidState();
    } catch (DataAccessException ex) {
      throw redisUnavailable(ex);
    }
  }

  private ConsumeStatus consumeStatus(String result) {
    if (result == null) {
      throw invalidState();
    }
    return switch (result) {
      case "CONSUMED" -> ConsumeStatus.CONSUMED;
      case "USED" -> ConsumeStatus.USED;
      case "REVOKED" -> ConsumeStatus.REVOKED;
      case "MISSING" -> ConsumeStatus.MISSING;
      default -> throw invalidState();
    };
  }

  private Object[] stateArguments(RefreshTokenState state) {
    return new Object[] {state.tokenId(), state.subject(), state.meetingId(),
        Long.toString(state.absoluteExpiresAt().toEpochMilli()),
        Long.toString(state.idleExpiresAt().toEpochMilli()), state.status().name()};
  }

  private RefreshTokenState loadState(StringRedisTemplate redisTemplate, String key) {
    Map<Object, Object> map = redisTemplate.opsForHash().entries(key);
    if (map == null || map.isEmpty()) {
      return null;
    }
    RefreshTokenState state;
    try {
      state = new RefreshTokenState(
          requiredValue(map, FIELD_TOKEN_ID), requiredValue(map, FIELD_SUBJECT), requiredValue(map, FIELD_MEETING_ID),
          Instant.ofEpochMilli(Long.parseLong(requiredValue(map, FIELD_ABSOLUTE_EXPIRES_AT))),
          Instant.ofEpochMilli(Long.parseLong(requiredValue(map, FIELD_IDLE_EXPIRES_AT))),
          TokenStatus.valueOf(requiredValue(map, FIELD_STATUS)));
      if (!key.equals(key(state.tokenId()))) {
        throw invalidState();
      }
    } catch (IllegalArgumentException ex) {
      AuthTokenException failure = invalidState();
      failure.initCause(ex);
      throw failure;
    }
    String familyId = stringValue(map.get(FIELD_FAMILY_ID));
    if (familyId.isBlank()) {
      return state.withStatus(TokenStatus.REVOKED);
    }
    Map<Object, Object> root = familyId.equals(state.tokenId()) ? map : redisTemplate.opsForHash().entries(key(familyId));
    if (!familyId.equals(stringValue(root.get(FIELD_FAMILY_ID)))
        || !("ACTIVE".equals(stringValue(root.get(FIELD_STATUS))) || "USED".equals(stringValue(root.get(FIELD_STATUS))))) {
      return state.withStatus(TokenStatus.REVOKED);
    }
    return state;
  }

  private String requiredValue(Map<Object, Object> map, String field) {
    String value = stringValue(map.get(field));
    if (value.isBlank()) {
      throw invalidState();
    }
    return value;
  }

  private StringRedisTemplate requireRedisTemplate() {
    StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
    if (redisTemplate == null) {
      throw new RetryableRefreshTokenException(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.CONFIG_INCOMPATIBLE.code(),
          "Redis недоступен для атомарного учета токенов.");
    }
    return redisTemplate;
  }

  private RetryableRefreshTokenException redisUnavailable(DataAccessException ex) {
    return new RetryableRefreshTokenException(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.CONFIG_INCOMPATIBLE.code(),
        "Redis недоступен для атомарного учета токенов.", ex);
  }

  private AuthTokenException invalidState() {
    return new AuthTokenException(HttpStatus.SERVICE_UNAVAILABLE, ErrorCode.CONFIG_INCOMPATIBLE.code(),
        "Состояние refresh-токена в Redis недоступно или повреждено.");
  }

  private String stringValue(Object value) {
    return value == null ? "" : value.toString();
  }

  private String key(String tokenId) {
    return "auth:refresh:" + tokenId;
  }
}
