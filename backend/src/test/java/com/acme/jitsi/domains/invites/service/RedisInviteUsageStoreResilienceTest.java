package com.acme.jitsi.domains.invites.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.acme.jitsi.config.ResilienceConfig;
import com.acme.jitsi.domains.meetings.service.MeetingTokenException;
import java.time.Instant;
import java.util.Date;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(classes = RedisInviteUsageStoreResilienceTest.TestConfig.class)
@TestPropertySource(properties = {
    "app.resilience.invites.redis.retry.max-retries=2",
    "app.resilience.invites.redis.retry.delay=1ms",
    "app.resilience.invites.redis.retry.max-delay=10ms",
    "app.resilience.invites.redis.concurrency-limit=1"
})
class RedisInviteUsageStoreResilienceTest {

  @Autowired
  private InviteUsageStore inviteUsageStore;

  @Autowired
  private StringRedisTemplate redisTemplate;

  @Autowired
  private ValueOperations<String, String> valueOperations;

  private ExecutorService executor;

  @BeforeEach
  void setUp() {
    reset(redisTemplate, valueOperations);
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(redisTemplate.expireAt(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(Date.class)))
        .thenReturn(true);
    executor = Executors.newFixedThreadPool(2);
  }

  @AfterEach
  void tearDown() {
    executor.shutdownNow();
  }

  @Test
  void retriesTransientRedisFailureUntilSuccess() {
    InviteExchangeProperties.Invite invite = invite("retry-token", 5);

    when(valueOperations.increment("invites:usage:retry-token"))
        .thenThrow(new QueryTimeoutException("transient-1"))
        .thenThrow(new QueryTimeoutException("transient-2"))
        .thenReturn(1L);

    inviteUsageStore.consume(invite);

    verify(valueOperations, times(3)).increment("invites:usage:retry-token");
  }

  @Test
  void returnsConfigIncompatibleAfterRetryExhausted() {
    InviteExchangeProperties.Invite invite = invite("retry-exhausted-token", 5);

    when(valueOperations.increment("invites:usage:retry-exhausted-token"))
        .thenThrow(new QueryTimeoutException("transient-1"))
        .thenThrow(new QueryTimeoutException("transient-2"))
        .thenThrow(new QueryTimeoutException("transient-3"));

    assertThatThrownBy(() -> inviteUsageStore.consume(invite))
        .isInstanceOf(MeetingTokenException.class)
        .satisfies(ex -> assertThat(((MeetingTokenException) ex).errorCode()).isEqualTo("CONFIG_INCOMPATIBLE"));

    verify(valueOperations, times(3)).increment("invites:usage:retry-exhausted-token");
  }

  @Test
  void doesNotRetryNonRetryableInviteExhaustedScenario() {
    InviteExchangeProperties.Invite invite = invite("exhausted-token", 1);
    when(valueOperations.increment("invites:usage:exhausted-token")).thenReturn(2L);

    assertThatThrownBy(() -> inviteUsageStore.consume(invite))
        .isInstanceOf(MeetingTokenException.class)
        .satisfies(ex -> assertThat(((MeetingTokenException) ex).errorCode()).isEqualTo("INVITE_EXHAUSTED"));

    verify(valueOperations, times(1)).increment("invites:usage:exhausted-token");
  }

  @Test
  void appliesConcurrencyLimitForParallelConsumeCalls() throws Exception {
    InviteExchangeProperties.Invite invite = invite("parallel-token", 5);
    CountDownLatch firstEntered = new CountDownLatch(1);
    CountDownLatch releaseFirst = new CountDownLatch(1);
    AtomicInteger invocations = new AtomicInteger();

    when(valueOperations.increment("invites:usage:parallel-token")).thenAnswer(invocation -> {
      int callNumber = invocations.incrementAndGet();
      if (callNumber == 1) {
        firstEntered.countDown();
        releaseFirst.await(2, TimeUnit.SECONDS);
      }
      return 1L;
    });

    CompletableFuture<Void> first = CompletableFuture.runAsync(() -> inviteUsageStore.consume(invite), executor);
    assertThat(firstEntered.await(1, TimeUnit.SECONDS)).isTrue();

    CompletableFuture<Void> second = CompletableFuture.runAsync(() -> inviteUsageStore.consume(invite), executor);

    Thread.sleep(150);
    assertThat(invocations.get()).isEqualTo(1);

    releaseFirst.countDown();

    first.get(2, TimeUnit.SECONDS);
    second.get(2, TimeUnit.SECONDS);

    assertThat(invocations.get()).isEqualTo(2);
  }

  private InviteExchangeProperties.Invite invite(String token, int usageLimit) {
    InviteExchangeProperties.Invite invite = new InviteExchangeProperties.Invite();
    invite.setToken(token);
    invite.setMeetingId("meeting-1");
    invite.setUsageLimit(usageLimit);
    invite.setExpiresAt(Instant.now().plusSeconds(3600));
    return invite;
  }

  @Configuration
  @Import(ResilienceConfig.class)
  static class TestConfig {

    @Bean
    StringRedisTemplate redisTemplate() {
      return mock(StringRedisTemplate.class);
    }

    @Bean
    @SuppressWarnings("unchecked")
    ValueOperations<String, String> valueOperations() {
      return mock(ValueOperations.class);
    }

    @Bean
    InviteUsageStore inviteUsageStore(ObjectProvider<StringRedisTemplate> redisTemplateProvider) {
      return new RedisInviteUsageStore(redisTemplateProvider);
    }
  }
}
