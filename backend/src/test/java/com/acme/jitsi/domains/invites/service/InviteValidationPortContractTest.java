package com.acme.jitsi.domains.invites.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import com.acme.jitsi.domains.invites.infrastructure.DbInviteValidationAdapter;
import com.acme.jitsi.domains.invites.infrastructure.MeetingStateGuardInviteMeetingStatePort;
import com.acme.jitsi.domains.meetings.service.InviteExhaustedException;
import com.acme.jitsi.domains.meetings.service.InviteNotFoundException;
import com.acme.jitsi.domains.meetings.service.MeetingInvite;
import com.acme.jitsi.domains.meetings.service.MeetingInviteRepository;
import com.acme.jitsi.domains.meetings.service.MeetingInviteService;
import com.acme.jitsi.domains.meetings.service.MeetingRepository;
import com.acme.jitsi.domains.meetings.service.MeetingRole;
import com.acme.jitsi.domains.meetings.service.MeetingStateGuard;
import com.acme.jitsi.domains.meetings.service.MeetingTokenException;
import com.acme.jitsi.domains.meetings.usecase.ConsumeInviteAttemptExecutor;
import com.acme.jitsi.domains.meetings.usecase.ConsumeInviteCommand;
import com.acme.jitsi.domains.meetings.usecase.ConsumeInviteConcurrencyBoundary;
import com.acme.jitsi.domains.meetings.usecase.ConsumeInviteUseCase;
import com.acme.jitsi.shared.ErrorCode;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.sql.DataSource;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

class InviteValidationPortContractTest {

  @Test
  void validateReturnsMeetingIdInBothModes() {
    assertForFixtures(happyFixtures(), fixture -> {
      InviteValidationResult resolution = fixture.port().validate(fixture.token());

      assertThat(resolution.meetingId()).as(fixture.modeName()).isEqualTo("meeting-a");
    });
  }

  @Test
  void validateMapsNotFoundInBothModes() {
    assertForFixtures(notFoundFixtures(), fixture -> {
      assertStableInviteExchangeError(fixture, () -> fixture.port().validate(fixture.token()), HttpStatus.NOT_FOUND, ErrorCode.INVITE_NOT_FOUND.code());
    });
  }

  @Test
  void validateMapsExhaustedInBothModes() {
    try (PortFixture propertiesFixture = propertiesModeFixture(invite("invite-exhausted", 1, 0, false));
       PortFixture databaseFixture = databaseModeFixture(domainInvite("invite-exhausted", 1, 1, false))) {
      propertiesFixture.port().reserve(propertiesFixture.token());
      assertStableInviteExchangeError(
        propertiesFixture,
        () -> propertiesFixture.port().validate(propertiesFixture.token()),
        HttpStatus.CONFLICT,
        ErrorCode.INVITE_EXHAUSTED.code());

      assertStableInviteExchangeError(
        databaseFixture,
        () -> databaseFixture.port().validate(databaseFixture.token()),
        HttpStatus.CONFLICT,
        ErrorCode.INVITE_EXHAUSTED.code());
    }
  }

  @Test
  void validateMapsPreConsumedExhaustedInBothModes() {
    try (PortFixture propertiesFixture = propertiesModeFixture(invite("invite-preconsumed", 1, 1, false));
         PortFixture databaseFixture = databaseModeFixture(domainInvite("invite-preconsumed", 1, 1, false))) {
        assertStableInviteExchangeError(
          propertiesFixture,
          () -> propertiesFixture.port().validate(propertiesFixture.token()),
          HttpStatus.CONFLICT,
          ErrorCode.INVITE_EXHAUSTED.code());

        assertStableInviteExchangeError(
          databaseFixture,
          () -> databaseFixture.port().validate(databaseFixture.token()),
          HttpStatus.CONFLICT,
          ErrorCode.INVITE_EXHAUSTED.code());
    }
  }

  @Test
  void validatePrefersRevokedOverExpiredInBothModes() {
    InviteExchangeProperties.Invite revokedAndExpired = invite("invite-revoked-expired", 2, 0, true);
    revokedAndExpired.setExpiresAt(Instant.now().minusSeconds(60));

    MeetingInvite revokedAndExpiredDb = domainInvite("invite-revoked-expired", 2, 0, true);

    try (PortFixture propertiesFixture = propertiesModeFixture(revokedAndExpired);
         PortFixture databaseFixture = databaseModeFixture(revokedAndExpiredDb)) {
        assertStableInviteExchangeError(
          propertiesFixture,
          () -> propertiesFixture.port().validate(propertiesFixture.token()),
          HttpStatus.GONE,
          ErrorCode.INVITE_REVOKED.code());

        assertStableInviteExchangeError(
          databaseFixture,
          () -> databaseFixture.port().validate(databaseFixture.token()),
          HttpStatus.GONE,
          ErrorCode.INVITE_REVOKED.code());
    }
  }

  @Test
  void validatePrefersExpiredOverMeetingStateFailureInBothModes() {
    InviteExchangeProperties.Invite expiredInvite = invite("invite-expired-ended", 2, 0, false);
    expiredInvite.setExpiresAt(Instant.now().minusSeconds(60));

    try (PortFixture propertiesFixture = propertiesModeFixture(expiredInvite, Set.of(), Set.of(), Set.of("meeting-a"));
         PortFixture databaseFixture = databaseModeFixture(
             domainInvite("invite-expired-ended", 2, 0, false, Instant.now().minusSeconds(60)),
             true)) {
        assertStableInviteExchangeError(
          propertiesFixture,
          () -> propertiesFixture.port().validate(propertiesFixture.token()),
          HttpStatus.GONE,
          ErrorCode.INVITE_EXPIRED.code());

        assertStableInviteExchangeError(
          databaseFixture,
          () -> databaseFixture.port().validate(databaseFixture.token()),
          HttpStatus.GONE,
          ErrorCode.INVITE_EXPIRED.code());
    }
  }

  @Test
  void reserveReturnsReservationInBothModes() {
    assertForFixtures(happyFixtures(), fixture -> {
      InviteReservation reservation = fixture.port().reserve(fixture.token());

      assertThat(reservation.inviteToken()).as(fixture.modeName()).isEqualTo(fixture.token());
      assertThat(reservation.meetingId()).as(fixture.modeName()).isEqualTo("meeting-a");
    });
  }

  @Test
  void reserveMapsNotFoundInBothModes() {
    try (PortFixture propertiesFixture = propertiesModeFixture(null);
         PortFixture databaseFixture = databaseModeFixture(null)) {
      assertStableInviteExchangeError(
        propertiesFixture,
        () -> propertiesFixture.port().reserve(propertiesFixture.token()),
        HttpStatus.NOT_FOUND,
        ErrorCode.INVITE_NOT_FOUND.code());

      assertStableInviteExchangeError(
        databaseFixture,
        () -> databaseFixture.port().reserve(databaseFixture.token()),
        HttpStatus.NOT_FOUND,
        ErrorCode.INVITE_NOT_FOUND.code());
    }
  }

  @Test
  void reserveMapsExhaustedInBothModes() {
    try (PortFixture propertiesFixture = propertiesModeFixture(invite("invite-single", 1, 0, false));
       PortFixture databaseFixture = databaseModeFixture(domainInvite("invite-single", 1, 0, false))) {
      propertiesFixture.port().reserve(propertiesFixture.token());
      assertStableInviteExchangeError(
        propertiesFixture,
        () -> propertiesFixture.port().reserve(propertiesFixture.token()),
        HttpStatus.CONFLICT,
        ErrorCode.INVITE_EXHAUSTED.code());

      databaseFixture.port().reserve(databaseFixture.token());
      assertStableInviteExchangeError(
        databaseFixture,
        () -> databaseFixture.port().reserve(databaseFixture.token()),
        HttpStatus.CONFLICT,
        ErrorCode.INVITE_EXHAUSTED.code());
    }
  }

  @Test
  void reserveUsesSharedMeetingTokenContractAcrossModes() {
    try (PortFixture propertiesFixture = propertiesModeFixture(null);
         PortFixture databaseFixture = databaseModeFixture(null)) {
        assertStableInviteExchangeError(
          propertiesFixture,
          () -> propertiesFixture.port().reserve(propertiesFixture.token()),
          HttpStatus.NOT_FOUND,
          ErrorCode.INVITE_NOT_FOUND.code());

        assertStableInviteExchangeError(
          databaseFixture,
          () -> databaseFixture.port().reserve(databaseFixture.token()),
          HttpStatus.NOT_FOUND,
          ErrorCode.INVITE_NOT_FOUND.code());
    }
  }

  @Test
  void reserveKeepsStableExhaustedContractForSaveLevelContentionInDatabaseMode() {
    try (PortFixture databaseFixture =
             databaseModeFixture(domainInvite("invite-contention", 1, 0, false), false, true)) {
      assertThatThrownBy(() -> databaseFixture.port().reserve(databaseFixture.token()))
          .isInstanceOf(InviteExchangeException.class)
          .satisfies(error -> {
            InviteExchangeException exception = (InviteExchangeException) error;
            assertThat(exception.status()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(exception.errorCode()).isEqualTo(ErrorCode.INVITE_EXHAUSTED.code());
            assertThat(exception.getCause()).isInstanceOf(InviteExhaustedException.class);
            assertThat(exception.getCause())
                .hasCauseInstanceOf(ObjectOptimisticLockingFailureException.class);
          });
    }
  }

  @Test
  void reserveAndRollbackHideModeSpecificConsumptionDetails() {
    assertForFixtures(singleUseFixtures(), fixture -> {
      InviteReservation reservation = fixture.port().reserve(fixture.token());

        assertStableInviteExchangeError(
          fixture,
          () -> fixture.port().reserve(fixture.token()),
          HttpStatus.CONFLICT,
          ErrorCode.INVITE_EXHAUSTED.code());

      fixture.port().rollback(reservation);

      InviteReservation retriedReservation = fixture.port().reserve(fixture.token());
      assertThat(retriedReservation.meetingId()).as(fixture.modeName()).isEqualTo("meeting-a");
    });
  }

  @Test
  void rollbackAcceptsNullReservationInBothModes() {
    assertForFixtures(happyFixtures(), fixture ->
        assertThatCode(() -> fixture.port().rollback(null)).as(fixture.modeName()).doesNotThrowAnyException());
  }

  @Test
  void rollbackMakesSingleUseInviteReservableAgainInBothModes() {
    assertForFixtures(singleUseFixtures(), fixture -> {
      InviteReservation reservation = fixture.port().reserve(fixture.token());
      fixture.port().rollback(reservation);

      InviteReservation retriedReservation = fixture.port().reserve(fixture.token());

      assertThat(retriedReservation.meetingId()).as(fixture.modeName()).isEqualTo("meeting-a");
    });
  }

  private void assertForFixtures(List<PortFixture> fixtures, FixtureAssertion assertion) {
    try {
      for (PortFixture fixture : fixtures) {
        assertion.accept(fixture);
      }
    } finally {
      fixtures.forEach(PortFixture::close);
    }
  }

  private void assertStableInviteExchangeError(
      PortFixture fixture,
      ThrowingCall call,
      HttpStatus expectedStatus,
      String expectedErrorCode) {
    assertThatThrownBy(call::run)
        .as(fixture.modeName())
        .isInstanceOf(InviteExchangeException.class)
        .satisfies(error -> {
          InviteExchangeException exception = (InviteExchangeException) error;
          assertThat(exception.status()).isEqualTo(expectedStatus);
          assertThat(exception.errorCode()).isEqualTo(expectedErrorCode);
        });
  }

  private List<PortFixture> happyFixtures() {
    return List.of(propertiesModeFixture(invite("invite-happy", 2, 0, false)), databaseModeFixture(domainInvite("invite-happy", 2, 0, false)));
  }

  private List<PortFixture> singleUseFixtures() {
    return List.of(propertiesModeFixture(invite("invite-single", 1, 0, false)), databaseModeFixture(domainInvite("invite-single", 1, 0, false)));
  }

  private List<PortFixture> notFoundFixtures() {
    return List.of(propertiesModeFixture(null), databaseModeFixture(null));
  }

  private PortFixture propertiesModeFixture(InviteExchangeProperties.Invite invite) {
    return propertiesModeFixture(invite, Set.of("meeting-a"), Set.of(), Set.of());
  }

  private PortFixture propertiesModeFixture(
      InviteExchangeProperties.Invite invite,
      Set<String> knownMeetingIds,
      Set<String> canceledMeetingIds,
      Set<String> closedMeetingIds) {
    InviteExchangeProperties properties = new InviteExchangeProperties();
    properties.setAtomicStore("in-memory");
    properties.setKnownMeetingIds(knownMeetingIds);
    properties.setCanceledMeetingIds(canceledMeetingIds);
    properties.setClosedMeetingIds(closedMeetingIds);
    properties.setInvites(invite == null ? List.of() : List.of(invite));

    @SuppressWarnings("unchecked")
    ObjectProvider<org.springframework.data.redis.core.StringRedisTemplate> provider = mock(ObjectProvider.class);
    InviteUsageStoreRouter router = new InviteUsageStoreRouter(
        new InviteUsageStoreResolver(properties, new InMemoryInviteUsageStore(), new RedisInviteUsageStore(provider), new com.acme.jitsi.domains.store.StoreSelectionStrategyFactory()));

    InviteValidationPort port = new InviteValidationService(properties, router, createValidationChain());
    String token = invite == null ? "missing-token" : invite.token();
    return new PortFixture("properties-mode", token, port, () -> {
    });
  }

  private PortFixture databaseModeFixture(MeetingInvite invite) {
    return databaseModeFixture(invite, false, false);
  }

  private PortFixture databaseModeFixture(MeetingInvite invite, boolean failMeetingStateGuard) {
    return databaseModeFixture(invite, failMeetingStateGuard, false);
  }

  private PortFixture databaseModeFixture(
      MeetingInvite invite,
      boolean failMeetingStateGuard,
      boolean failOnSaveWithOptimisticLocking) {
    InMemoryMeetingInviteRepository repository =
        new InMemoryMeetingInviteRepository(invite == null ? List.of() : List.of(invite));
    if (failOnSaveWithOptimisticLocking) {
      repository.failOnSaveWithOptimisticLocking();
    }
    MeetingStateGuard meetingStateGuard = mock(MeetingStateGuard.class);
    if (failMeetingStateGuard) {
      doThrow(new MeetingTokenException(HttpStatus.CONFLICT, ErrorCode.MEETING_ENDED.code(), "Встреча завершена."))
          .when(meetingStateGuard).assertJoinAllowed("meeting-a");
    }
    MeetingRepository meetingRepository = mock(MeetingRepository.class);
    AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
    context.register(DatabaseModeRuntimeConfig.class);
    context.registerBean(DataSource.class, () -> {
      JdbcDataSource dataSource = new JdbcDataSource();
      dataSource.setURL("jdbc:h2:mem:invite-validation-contract-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
      dataSource.setUser("sa");
      dataSource.setPassword("");
      return dataSource;
    });
    context.registerBean(PlatformTransactionManager.class,
        () -> new DataSourceTransactionManager(context.getBean(DataSource.class)));
    context.registerBean(MeetingInviteRepository.class, () -> repository);
    context.registerBean(MeetingStateGuard.class, () -> meetingStateGuard);
    context.registerBean(MeetingRepository.class, () -> meetingRepository);
    context.registerBean(MeetingInviteService.class,
        () -> new MeetingInviteService(context.getBean(MeetingInviteRepository.class), context.getBean(MeetingRepository.class)));
    context.registerBean(ConsumeInviteAttemptExecutor.class,
      () -> new ConsumeInviteAttemptExecutor(
        context.getBean(MeetingInviteRepository.class),
        context.getBean(MeetingStateGuard.class)));
    context.registerBean(ConsumeInviteConcurrencyBoundary.class,
      () -> new ConsumeInviteConcurrencyBoundary(context.getBean(ConsumeInviteAttemptExecutor.class)));
    context.registerBean(ConsumeInviteUseCase.class,
      () -> new ConsumeInviteUseCase(context.getBean(ConsumeInviteConcurrencyBoundary.class)));
    context.registerBean(InviteMeetingStatePort.class,
      () -> new MeetingStateGuardInviteMeetingStatePort(context.getBean(MeetingStateGuard.class)));
    context.registerBean(InviteValidationPort.class,
      () -> new DbInviteValidationAdapter(
        context.getBean(MeetingInviteService.class),
        context.getBean(ConsumeInviteUseCase.class),
        context.getBean(InviteMeetingStatePort.class)));
    context.refresh();

    if (!AopUtils.isAopProxy(context.getBean(ConsumeInviteConcurrencyBoundary.class))) {
      context.close();
      throw new IllegalStateException("Database-mode fixture must use Spring proxied consume-invite concurrency boundary");
    }

    InviteValidationPort port = context.getBean(InviteValidationPort.class);
    String token = invite == null ? "missing-token" : invite.token();
    return new PortFixture("database-mode", token, port, context::close);
  }

  private InviteValidationChain createValidationChain() {
    return new InviteValidationChain(List.of(
        new InviteTokenBlankValidator(),
        new InviteTokenExistsValidator(),
        new InviteRevokedValidator(),
        new InviteExpirationValidator(),
        new InviteMeetingKnownValidator(),
          new InviteMeetingStateValidator(mock(InviteMeetingStatePort.class)),
        new InviteUsageLimitValidator()));
  }

  private InviteExchangeProperties.Invite invite(String token, int usageLimit, int usedCount, boolean revoked) {
    InviteExchangeProperties.Invite invite = new InviteExchangeProperties.Invite();
    invite.setToken(token);
    invite.setMeetingId("meeting-a");
    invite.setExpiresAt(Instant.now().plusSeconds(3600));
    invite.setUsageLimit(usageLimit);
    invite.setUsedCount(usedCount);
    invite.setRevoked(revoked);
    return invite;
  }

  private MeetingInvite domainInvite(String token, int maxUses, int usedCount, boolean revoked) {
    return domainInvite(token, maxUses, usedCount, revoked, Instant.now().plusSeconds(3600));
  }

  private MeetingInvite domainInvite(
      String token,
      int maxUses,
      int usedCount,
      boolean revoked,
      Instant expiresAt) {
    return new MeetingInvite(
        "invite-id-" + token,
        "meeting-a",
        token,
        MeetingRole.PARTICIPANT,
        maxUses,
        usedCount,
        expiresAt,
        revoked ? Instant.now().minusSeconds(60) : null,
        Instant.now().minusSeconds(3600),
        "tester",
        null,
        null,
        0L);
  }

  @Configuration
  @EnableRetry(proxyTargetClass = true)
  @EnableTransactionManagement(proxyTargetClass = true)
  static class DatabaseModeRuntimeConfig {
  }

  private record PortFixture(String modeName, String token, InviteValidationPort port, Runnable closeAction)
      implements AutoCloseable {

    @Override
    public void close() {
      closeAction.run();
    }
  }

  @FunctionalInterface
  private interface ThrowingCall {
    void run();
  }

  @FunctionalInterface
  private interface FixtureAssertion {
    void accept(PortFixture fixture);
  }

  private static final class InMemoryMeetingInviteRepository implements MeetingInviteRepository {

    private final Map<String, MeetingInvite> invitesByToken = new HashMap<>();
    private final Map<String, String> tokensById = new HashMap<>();
    private boolean failOnSaveWithOptimisticLocking;

    InMemoryMeetingInviteRepository(List<MeetingInvite> invites) {
      for (MeetingInvite invite : invites) {
        store(invite);
      }
    }

    void failOnSaveWithOptimisticLocking() {
      failOnSaveWithOptimisticLocking = true;
    }

    @Override
    public MeetingInvite save(MeetingInvite invite) {
      if (failOnSaveWithOptimisticLocking) {
        throw new ObjectOptimisticLockingFailureException(MeetingInvite.class, invite.id());
      }
      store(invite);
      return invite;
    }

    @Override
    public List<MeetingInvite> saveAll(List<MeetingInvite> invites) {
      invites.forEach(this::store);
      return invites;
    }

    @Override
    public Optional<MeetingInvite> findById(String id) {
      String token = tokensById.get(id);
      return token == null ? Optional.empty() : Optional.ofNullable(invitesByToken.get(token));
    }

    @Override
    public Optional<MeetingInvite> findByToken(String token) {
      return Optional.ofNullable(invitesByToken.get(token));
    }

    @Override
    public List<MeetingInvite> findByMeetingId(String meetingId, int page, int size) {
      return invitesByToken.values().stream().filter(invite -> invite.meetingId().equals(meetingId)).toList();
    }

    @Override
    public Optional<MeetingInvite> findActiveByMeetingIdAndRecipientEmail(String meetingId, String recipientEmail, Instant now) {
      return Optional.empty();
    }

    @Override
    public Optional<MeetingInvite> findActiveByMeetingIdAndRecipientUserId(String meetingId, String recipientUserId, Instant now) {
      return Optional.empty();
    }

    @Override
    public long countByMeetingId(String meetingId) {
      return invitesByToken.values().stream().filter(invite -> invite.meetingId().equals(meetingId)).count();
    }

    @Override
    public boolean existsByMeetingId(String meetingId) {
      return invitesByToken.values().stream().anyMatch(invite -> invite.meetingId().equals(meetingId));
    }

    private void store(MeetingInvite invite) {
      invitesByToken.put(invite.token(), invite);
      tokensById.put(invite.id(), invite.token());
    }
  }
}