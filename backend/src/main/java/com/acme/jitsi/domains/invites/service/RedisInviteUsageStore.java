package com.acme.jitsi.domains.invites.service;

import com.acme.jitsi.domains.meetings.service.MeetingTokenException;
import java.util.Date;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.resilience.annotation.ConcurrencyLimit;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Component;

@Component
@Retryable(
  includes = RetryableInviteUsageException.class,
  maxRetriesString = "${app.resilience.invites.redis.retry.max-retries:2}",
  delayString = "${app.resilience.invites.redis.retry.delay:100ms}",
  maxDelayString = "${app.resilience.invites.redis.retry.max-delay:1s}")
@ConcurrencyLimit(limitString = "${app.resilience.invites.redis.concurrency-limit:32}")
class RedisInviteUsageStore implements InviteUsageStore {

  private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;

  RedisInviteUsageStore(ObjectProvider<StringRedisTemplate> redisTemplateProvider) {
    this.redisTemplateProvider = redisTemplateProvider;
  }

  @Override
  public void assertCanConsume(InviteExchangeProperties.Invite invite) {
    StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
    if (redisTemplate == null) {
      return;
    }

    String key = "invites:usage:" + invite.token();
    try {
      String currentRaw = redisTemplate.opsForValue().get(key);
      long current = currentRaw == null ? 0L : Long.parseLong(currentRaw);
      if (current >= invite.usageLimit()) {
        throw new MeetingTokenException(HttpStatus.CONFLICT, "INVITE_EXHAUSTED", "Лимит использований инвайта исчерпан.");
      }
    } catch (NumberFormatException ex) {
      throw new RetryableInviteUsageException(
          HttpStatus.INTERNAL_SERVER_ERROR,
          "CONFIG_INCOMPATIBLE",
          "Не удалось прочитать счетчик использований инвайта.",
          ex);
    } catch (DataAccessException ex) {
      throw new RetryableInviteUsageException(
          HttpStatus.INTERNAL_SERVER_ERROR,
          "CONFIG_INCOMPATIBLE",
          "Не удалось выполнить проверку лимита инвайта.",
          ex);
    }
  }

  @Override
  public void consume(InviteExchangeProperties.Invite invite) {
    StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
    requireRedisTemplate(redisTemplate);

    String key = "invites:usage:" + invite.token();
    try {
      Long used = redisTemplate.opsForValue().increment(key);
      long usageCount = requireUsageCount(used);
      applyInitialExpiry(redisTemplate, key, invite, usageCount);
      assertWithinUsageLimit(invite, usageCount);
    } catch (DataAccessException ex) {
      throw new RetryableInviteUsageException(
          HttpStatus.INTERNAL_SERVER_ERROR,
          "CONFIG_INCOMPATIBLE",
          "Не удалось выполнить атомарный учет инвайта.",
          ex);
    }
  }

  private void requireRedisTemplate(StringRedisTemplate redisTemplate) {
    if (redisTemplate == null) {
      throw new MeetingTokenException(
          HttpStatus.INTERNAL_SERVER_ERROR,
          "CONFIG_INCOMPATIBLE",
          "Redis недоступен для атомарного учета инвайтов.");
    }
  }

  private long requireUsageCount(Long used) {
    if (used == null) {
      throw new RetryableInviteUsageException(
          HttpStatus.INTERNAL_SERVER_ERROR,
          "CONFIG_INCOMPATIBLE",
          "Не удалось выполнить атомарный учет инвайта.");
    }
    return used;
  }

  private void applyInitialExpiry(
      StringRedisTemplate redisTemplate,
      String key,
      InviteExchangeProperties.Invite invite,
      long usageCount) {
    if (usageCount == 1L && invite.expiresAt() != null) {
      redisTemplate.expireAt(key, Date.from(invite.expiresAt()));
    }
  }

  private void assertWithinUsageLimit(InviteExchangeProperties.Invite invite, long usageCount) {
    if (usageCount > invite.usageLimit()) {
      throw new MeetingTokenException(HttpStatus.CONFLICT, "INVITE_EXHAUSTED", "Лимит использований инвайта исчерпан.");
    }
  }

  @Override
  public void rollback(String inviteToken) {
    StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
    if (redisTemplate == null) {
      return;
    }

    String key = "invites:usage:" + inviteToken;
    try {
      Long used = redisTemplate.opsForValue().increment(key, -1);
      if (used != null && used <= 0) {
        redisTemplate.delete(key);
      }
    } catch (DataAccessException ignored) {
    }
  }

}