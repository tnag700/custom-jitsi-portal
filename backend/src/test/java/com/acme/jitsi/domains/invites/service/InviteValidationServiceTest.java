package com.acme.jitsi.domains.invites.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.acme.jitsi.domains.meetings.service.MeetingStateGuard;
import com.acme.jitsi.domains.store.StoreSelectionStrategyFactory;
import com.acme.jitsi.domains.meetings.service.MeetingTokenException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class InviteValidationServiceTest {

  @Test
  void oneTimeInviteIsConsumedAtomicallyUnderConcurrentRequests() throws Exception {
    InviteExchangeProperties properties = new InviteExchangeProperties();
    InviteExchangeProperties.Invite invite = new InviteExchangeProperties.Invite();
    invite.setToken("invite-race");
    invite.setMeetingId("meeting-a");
    invite.setExpiresAt(Instant.now().plusSeconds(3600));
    invite.setUsageLimit(1);
    properties.setAtomicStore("in-memory");
    properties.setInvites(List.of(invite));
    properties.setKnownMeetingIds(Set.of("meeting-a"));

    @SuppressWarnings("unchecked")
    ObjectProvider<org.springframework.data.redis.core.StringRedisTemplate> provider = mock(ObjectProvider.class);
    when(provider.getIfAvailable()).thenReturn(null);

    InMemoryInviteUsageStore inMemoryStore = new InMemoryInviteUsageStore();
    RedisInviteUsageStore redisStore = new RedisInviteUsageStore(provider);
    InviteUsageStoreResolver resolver =
      new InviteUsageStoreResolver(properties, inMemoryStore, redisStore, new StoreSelectionStrategyFactory());
    InviteUsageStoreRouter storeRouter = new InviteUsageStoreRouter(resolver);
    InviteValidationService service = new InviteValidationService(properties, storeRouter, createValidationChain());
    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);
    List<Future<String>> futures = new ArrayList<>();
    try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
      for (int i = 0; i < 2; i++) {
        futures.add(pool.submit(() -> {
          ready.countDown();
          start.await();
          try {
            service.validateAndConsume("invite-race");
            return "ok";
          } catch (MeetingTokenException ex) {
            return ex.errorCode();
          }
        }));
      }

      ready.await();
      start.countDown();

      List<String> outcomes = new ArrayList<>();
      for (Future<String> future : futures) {
        outcomes.add(future.get());
      }

      assertThat(outcomes).containsExactlyInAnyOrder("ok", "INVITE_EXHAUSTED");
    }
  }

  @Test
  void redisAtomicModeWithoutRedisFailsWithConfigIncompatible() {
    InviteExchangeProperties properties = new InviteExchangeProperties();
    InviteExchangeProperties.Invite invite = new InviteExchangeProperties.Invite();
    invite.setToken("invite-redis");
    invite.setMeetingId("meeting-a");
    invite.setExpiresAt(Instant.now().plusSeconds(3600));
    invite.setUsageLimit(1);
    properties.setAtomicStore("redis");
    properties.setInvites(List.of(invite));
    properties.setKnownMeetingIds(Set.of("meeting-a"));

    @SuppressWarnings("unchecked")
    ObjectProvider<org.springframework.data.redis.core.StringRedisTemplate> provider = mock(ObjectProvider.class);
    when(provider.getIfAvailable()).thenReturn(null);

    InMemoryInviteUsageStore inMemoryStore = new InMemoryInviteUsageStore();
    RedisInviteUsageStore redisStore = new RedisInviteUsageStore(provider);
    InviteUsageStoreResolver resolver =
      new InviteUsageStoreResolver(properties, inMemoryStore, redisStore, new StoreSelectionStrategyFactory());
    InviteUsageStoreRouter storeRouter = new InviteUsageStoreRouter(resolver);
    InviteValidationService service = new InviteValidationService(properties, storeRouter, createValidationChain());

    assertThatThrownBy(() -> service.validateAndConsume("invite-redis"))
        .isInstanceOf(MeetingTokenException.class)
        .extracting(error -> ((MeetingTokenException) error).errorCode())
        .isEqualTo("CONFIG_INCOMPATIBLE");
  }

  @Test
  void rollbackDelegatesToUsageStoreRouter() {
    InviteExchangeProperties properties = new InviteExchangeProperties();
    InviteUsageStoreRouter storeRouter = mock(InviteUsageStoreRouter.class);
    InviteValidationService service = new InviteValidationService(properties, storeRouter, createValidationChain());

    service.rollback(new InviteValidationService.InviteReservation("invite-rollback", "meeting-a"));

    verify(storeRouter).rollback("invite-rollback");
  }

  private InviteValidationChain createValidationChain() {
    return new InviteValidationChain(List.of(
        new InviteTokenBlankValidator(),
        new InviteTokenExistsValidator(),
        new InviteRevokedValidator(),
        new InviteExpirationValidator(),
        new InviteMeetingKnownValidator(),
        new InviteMeetingStateValidator(mock(MeetingStateGuard.class)),
        new InviteUsageLimitValidator()));
  }
}