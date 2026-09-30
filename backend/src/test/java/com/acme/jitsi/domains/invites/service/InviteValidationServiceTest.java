package com.acme.jitsi.domains.invites.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.acme.jitsi.domains.store.StoreSelectionStrategyFactory;
import com.acme.jitsi.shared.ErrorCode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;

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
    InviteValidationService service = new InviteValidationService(properties, storeRouter, mock(InviteMeetingStatePort.class));
    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);
    List<Future<String>> futures = new ArrayList<>();
    try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
      for (int i = 0; i < 2; i++) {
        futures.add(pool.submit(() -> {
          ready.countDown();
          start.await();
          try {
            service.reserve("invite-race");
            return "ok";
          } catch (InviteExchangeException ex) {
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

      assertThat(outcomes).containsExactlyInAnyOrder("ok", ErrorCode.INVITE_EXHAUSTED.code());
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
    InviteValidationService service = new InviteValidationService(properties, storeRouter, mock(InviteMeetingStatePort.class));

    assertThatThrownBy(() -> service.reserve("invite-redis"))
        .isInstanceOf(InviteExchangeException.class)
        .extracting(error -> ((InviteExchangeException) error).errorCode())
      .isEqualTo(ErrorCode.CONFIG_INCOMPATIBLE.code());
  }

  @Test
  void rollbackDelegatesToUsageStoreRouter() {
    InviteExchangeProperties properties = new InviteExchangeProperties();
    InviteExchangeProperties.Invite invite = new InviteExchangeProperties.Invite();
    invite.setToken("invite-rollback");
    invite.setMeetingId("meeting-a");
    invite.setExpiresAt(Instant.now().plusSeconds(3600));
    invite.setUsageLimit(2);
    properties.setInvites(List.of(invite));
    properties.setKnownMeetingIds(Set.of("meeting-a"));
    InviteUsageStoreRouter storeRouter = mock(InviteUsageStoreRouter.class);
    InviteValidationService service = new InviteValidationService(properties, storeRouter, mock(InviteMeetingStatePort.class));

    when(storeRouter).thenReturn(storeRouter);

    InviteReservation reservation = service.reserve("invite-rollback");

    service.rollback(reservation);

    verify(storeRouter).consume(invite);
    verify(storeRouter).rollback("invite-rollback");
  }

  @Test
  void rollbackIgnoresReservationThatWasNotIssuedByService() {
    InviteExchangeProperties properties = new InviteExchangeProperties();
    InviteUsageStoreRouter storeRouter = mock(InviteUsageStoreRouter.class);
    InviteValidationService service = new InviteValidationService(properties, storeRouter, mock(InviteMeetingStatePort.class));

    service.rollback(InviteReservation.issue("forged", "invite-rollback", "meeting-a"));

    verifyNoMoreInteractions(storeRouter);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", "missing", " invite-good "})
  void invalidTokensDoNotConsumeInvite(String token) {
    InviteExchangeProperties.Invite invite = InviteValidationTestFixtures.invite(
        "invite-good", "meeting-a", null, false, 1);
    InviteExchangeProperties properties = InviteValidationTestFixtures.propertiesWithInvites(List.of(invite));
    properties.setKnownMeetingIds(Set.of("meeting-a"));
    InviteValidationService service = propertiesService(properties, mock(InviteMeetingStatePort.class));

    assertInviteError(() -> service.reserve(token), HttpStatus.NOT_FOUND, ErrorCode.INVITE_NOT_FOUND.code());
    assertThat(service.reserve("invite-good").meetingId()).isEqualTo("meeting-a");
  }

  @ParameterizedTest
  @CsvSource({
      "revoked, 410, INVITE_REVOKED",
      "expired, 410, INVITE_EXPIRED",
      "unknown, 404, MEETING_NOT_FOUND",
      "canceled, 409, MEETING_CANCELED",
      "closed, 409, MEETING_ENDED"
  })
  void configuredFailuresPrecedeExhaustionAndDoNotConsume(String failure, int status, String errorCode) {
    InviteExchangeProperties.Invite invite = InviteValidationTestFixtures.invite(
        "invite-good", "meeting-a", null, false, 1);
    invite.setUsedCount(1);
    InviteExchangeProperties properties = InviteValidationTestFixtures.propertiesWithInvites(List.of(invite));
    properties.setKnownMeetingIds(Set.of("meeting-a"));
    switch (failure) {
      case "revoked" -> invite.setRevoked(true);
      case "expired" -> invite.setExpiresAt(Instant.EPOCH);
      case "unknown" -> properties.setKnownMeetingIds(Set.of("meeting-other"));
      case "canceled" -> {
        properties.setCanceledMeetingIds(Set.of("meeting-a"));
        properties.setClosedMeetingIds(Set.of("meeting-a"));
      }
      case "closed" -> properties.setClosedMeetingIds(Set.of("meeting-a"));
      default -> throw new IllegalArgumentException(failure);
    }
    InviteMeetingStatePort meetingState = mock(InviteMeetingStatePort.class);
    doThrow(new IllegalStateException("Runtime meeting guard must be skipped"))
        .when(meetingState).assertJoinAllowed("meeting-a");
    InviteValidationService service = propertiesService(properties, meetingState);

    assertInviteError(() -> service.reserve("invite-good"), HttpStatus.valueOf(status), errorCode);

    invite.setRevoked(false);
    invite.setExpiresAt(null);
    invite.setUsedCount(0);
    properties.setKnownMeetingIds(Set.of("meeting-a"));
    properties.setCanceledMeetingIds(Set.of());
    properties.setClosedMeetingIds(Set.of());
    assertThat(service.reserve("invite-good").meetingId()).isEqualTo("meeting-a");
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"MEETING_ENDED", "MEETING_CANCELED"})
  void emptyKnownIdsConsultRuntimeMeetingGuard(String errorCode) {
    InviteExchangeProperties.Invite invite = InviteValidationTestFixtures.invite(
        "invite-good", "meeting-a", null, false, 1);
    InviteExchangeProperties properties = InviteValidationTestFixtures.propertiesWithInvites(List.of(invite));
    InviteMeetingStatePort meetingState = mock(InviteMeetingStatePort.class);
    InviteExchangeException failure = new InviteExchangeException(
        HttpStatus.CONFLICT, errorCode, "Runtime meeting rejection");
    if (errorCode != null) {
      invite.setUsedCount(1);
      doThrow(failure).when(meetingState).assertJoinAllowed("meeting-a");
    }
    InviteValidationService service = propertiesService(properties, meetingState);

    if (errorCode != null) {
      assertThatThrownBy(() -> service.validate("invite-good")).isSameAs(failure);
    } else {
      assertThat(service.validate("invite-good").meetingId()).isEqualTo("meeting-a");
    }
    verify(meetingState).assertJoinAllowed("meeting-a");
  }

  @Test
  void validationChecksLimitWithoutSpendingIt() {
    InviteExchangeProperties.Invite invite = InviteValidationTestFixtures.invite(
        "invite-good", "meeting-a", null, false, 1);
    InviteExchangeProperties properties = InviteValidationTestFixtures.propertiesWithInvites(List.of(invite));
    properties.setKnownMeetingIds(Set.of("meeting-a"));
    InviteValidationService service = propertiesService(properties, mock(InviteMeetingStatePort.class));

    assertThat(service.validate("invite-good").meetingId()).isEqualTo("meeting-a");
    assertThat(service.validate("invite-good").meetingId()).isEqualTo("meeting-a");
    assertThat(service.reserve("invite-good").meetingId()).isEqualTo("meeting-a");
    assertInviteError(() -> service.validate("invite-good"), HttpStatus.CONFLICT, ErrorCode.INVITE_EXHAUSTED.code());
  }

  @Test
  void reserveResolvesAndValidatesAgainAfterPreflight() {
    InviteExchangeProperties.Invite invite = InviteValidationTestFixtures.invite(
        "invite-good", "meeting-a", null, false, 1);
    InviteExchangeProperties properties = InviteValidationTestFixtures.propertiesWithInvites(List.of(invite));
    properties.setKnownMeetingIds(Set.of("meeting-a"));
    InviteValidationService service = propertiesService(properties, mock(InviteMeetingStatePort.class));

    assertThat(service.validate("invite-good").meetingId()).isEqualTo("meeting-a");
    invite.setRevoked(true);
    assertInviteError(() -> service.reserve("invite-good"), HttpStatus.GONE, ErrorCode.INVITE_REVOKED.code());

    InviteExchangeProperties.Invite replacement = InviteValidationTestFixtures.invite(
        "invite-good", "meeting-b", null, false, 1);
    properties.setInvites(List.of(replacement));
    properties.setKnownMeetingIds(Set.of("meeting-b"));
    assertThat(service.reserve("invite-good").meetingId()).isEqualTo("meeting-b");
  }

  @Test
  void duplicateTokensUseFirstConfiguredInvite() {
    InviteExchangeProperties.Invite first = InviteValidationTestFixtures.invite(
        "invite-good", "meeting-a", null, true, 1);
    InviteExchangeProperties.Invite second = InviteValidationTestFixtures.invite(
        "invite-good", "meeting-b", null, false, 1);
    InviteExchangeProperties properties = InviteValidationTestFixtures.propertiesWithInvites(List.of(first, second));
    properties.setKnownMeetingIds(Set.of("meeting-a", "meeting-b"));
    InviteValidationService service = propertiesService(properties, mock(InviteMeetingStatePort.class));

    assertInviteError(() -> service.validate("invite-good"), HttpStatus.GONE, ErrorCode.INVITE_REVOKED.code());
    first.setRevoked(false);
    assertThat(service.validate("invite-good").meetingId()).isEqualTo("meeting-a");
  }

  private InviteValidationService propertiesService(
      InviteExchangeProperties properties, InviteMeetingStatePort meetingState) {
    properties.setAtomicStore("in-memory");
    @SuppressWarnings("unchecked")
    ObjectProvider<org.springframework.data.redis.core.StringRedisTemplate> provider = mock(ObjectProvider.class);
    InviteUsageStoreRouter router = new InviteUsageStoreRouter(new InviteUsageStoreResolver(
        properties, new InMemoryInviteUsageStore(), new RedisInviteUsageStore(provider), new StoreSelectionStrategyFactory()));
    return new InviteValidationService(properties, router, meetingState);
  }

  private static void assertInviteError(Runnable call, HttpStatus status, String errorCode) {
    assertThatThrownBy(call::run)
        .isInstanceOf(InviteExchangeException.class)
        .satisfies(error -> {
          InviteExchangeException exception = (InviteExchangeException) error;
          assertThat(exception.status()).isEqualTo(status);
          assertThat(exception.errorCode()).isEqualTo(errorCode);
        });
  }

}
