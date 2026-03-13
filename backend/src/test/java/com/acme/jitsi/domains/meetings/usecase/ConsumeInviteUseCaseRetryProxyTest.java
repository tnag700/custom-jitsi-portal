package com.acme.jitsi.domains.meetings.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.acme.jitsi.domains.meetings.service.InviteExhaustedException;
import com.acme.jitsi.domains.meetings.service.MeetingInvite;
import com.acme.jitsi.domains.meetings.service.MeetingInviteRepository;
import com.acme.jitsi.domains.meetings.service.MeetingRole;
import com.acme.jitsi.domains.meetings.service.MeetingStateGuard;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@SpringJUnitConfig(ConsumeInviteUseCaseRetryProxyTest.TestConfig.class)
class ConsumeInviteUseCaseRetryProxyTest {

  private static final String TOKEN = "invite-token";

  @jakarta.annotation.Resource
  private ConsumeInviteUseCase useCase;

  @jakarta.annotation.Resource
  private ConsumeInviteConcurrencyBoundary concurrencyBoundary;

  @jakarta.annotation.Resource
  private TestInviteRepository inviteRepository;

  @jakarta.annotation.Resource
  private OuterTransactionCaller outerTransactionCaller;

  @BeforeEach
  void setUp() {
    inviteRepository.reset();
    outerTransactionCaller.reset();
  }

  @Test
  void executeRetriesThroughSpringProxyWithFreshTransactionContext() {
    assertThat(AopUtils.isAopProxy(concurrencyBoundary)).isTrue();
    inviteRepository.failOnceOnFindWithOptimisticLocking();

    MeetingInvite consumed = useCase.execute(new ConsumeInviteCommand(TOKEN));

    assertThat(consumed.usedCount()).isEqualTo(1);
    assertThat(inviteRepository.findAttempts()).isEqualTo(2);
    assertThat(inviteRepository.findTransactionActiveFlags()).containsExactly(true, true);
    assertThat(inviteRepository.findTransactionResourceIds()).hasSize(2).doesNotHaveDuplicates();
  }

  @Test
  void executeMapsSaveLevelOptimisticLockingToStableExhaustedOutcomeAfterRetryExhaustion() {
    assertThat(AopUtils.isAopProxy(concurrencyBoundary)).isTrue();
    inviteRepository.failOnSaveWithOptimisticLocking();

    assertThatThrownBy(() -> useCase.execute(new ConsumeInviteCommand(TOKEN)))
        .isInstanceOf(InviteExhaustedException.class)
        .hasCauseInstanceOf(ObjectOptimisticLockingFailureException.class);

    assertThat(inviteRepository.findAttempts()).isEqualTo(3);
    assertThat(inviteRepository.saveAttempts()).isEqualTo(3);
    assertThat(inviteRepository.findTransactionActiveFlags()).containsExactly(true, true, true);
    assertThat(inviteRepository.findTransactionResourceIds()).hasSize(3).doesNotHaveDuplicates();
  }

  @Test
  void executeUsesFreshAttemptTransactionsEvenInsideAmbientTransaction() {
    assertThat(AopUtils.isAopProxy(concurrencyBoundary)).isTrue();
    inviteRepository.failOnceOnFindWithOptimisticLocking();

    MeetingInvite consumed =
        outerTransactionCaller.execute(useCase, new ConsumeInviteCommand(TOKEN));

    assertThat(consumed.usedCount()).isEqualTo(1);
    assertThat(outerTransactionCaller.transactionActive()).isTrue();
    assertThat(inviteRepository.findAttempts()).isEqualTo(2);
    assertThat(inviteRepository.findTransactionActiveFlags()).containsExactly(true, true);
    assertThat(inviteRepository.findTransactionResourceIds()).hasSize(2).doesNotHaveDuplicates();
    assertThat(inviteRepository.findTransactionResourceIds())
        .doesNotContain(outerTransactionCaller.transactionResourceId());
  }

  @Configuration
  @EnableRetry(proxyTargetClass = true)
  @EnableTransactionManagement(proxyTargetClass = true)
  static class TestConfig {

    @Bean
    DataSource dataSource() {
      JdbcDataSource dataSource = new JdbcDataSource();
      dataSource.setURL("jdbc:h2:mem:consume-invite-retry-proxy;DB_CLOSE_DELAY=-1");
      dataSource.setUser("sa");
      dataSource.setPassword("");
      return dataSource;
    }

    @Bean
    PlatformTransactionManager transactionManager(DataSource dataSource) {
      return new DataSourceTransactionManager(dataSource);
    }

    @Bean
    TestInviteRepository testInviteRepository(DataSource dataSource) {
      return new TestInviteRepository(dataSource);
    }

    @Bean
    MeetingStateGuard meetingStateGuard() {
      return mock(MeetingStateGuard.class);
    }

    @Bean
    ConsumeInviteAttemptExecutor consumeInviteAttemptExecutor(
        TestInviteRepository inviteRepository,
        MeetingStateGuard meetingStateGuard) {
      return new ConsumeInviteAttemptExecutor(inviteRepository, meetingStateGuard);
    }

    @Bean
    ConsumeInviteConcurrencyBoundary consumeInviteConcurrencyBoundary(
        ConsumeInviteAttemptExecutor attemptExecutor) {
      return new ConsumeInviteConcurrencyBoundary(attemptExecutor);
    }

    @Bean
    ConsumeInviteUseCase consumeInviteUseCase(
        ConsumeInviteConcurrencyBoundary concurrencyBoundary) {
      return new ConsumeInviteUseCase(concurrencyBoundary);
    }

    @Bean
    OuterTransactionCaller outerTransactionCaller(DataSource dataSource) {
      return new OuterTransactionCaller(dataSource);
    }
  }

  static class OuterTransactionCaller {

    private final DataSource dataSource;
    private boolean transactionActive;
    private int transactionResourceId;

    OuterTransactionCaller(DataSource dataSource) {
      this.dataSource = dataSource;
    }

    void reset() {
      transactionActive = false;
      transactionResourceId = 0;
    }

    boolean transactionActive() {
      return transactionActive;
    }

    int transactionResourceId() {
      return transactionResourceId;
    }

    @Transactional
    MeetingInvite execute(ConsumeInviteUseCase useCase, ConsumeInviteCommand command) {
      transactionActive = TransactionSynchronizationManager.isActualTransactionActive();
      Object resource = TransactionSynchronizationManager.getResource(dataSource);
      transactionResourceId = System.identityHashCode(resource);
      return useCase.execute(command);
    }
  }

  static final class TestInviteRepository implements MeetingInviteRepository {

    private final DataSource dataSource;
    private final MeetingInvite baseInvite;
    private final List<Boolean> findTransactionActiveFlags = new ArrayList<>();
    private final List<Integer> findTransactionResourceIds = new ArrayList<>();

    private boolean failFindOnce;
    private boolean failOnSave;
    private int findAttempts;
    private int saveAttempts;

    TestInviteRepository(DataSource dataSource) {
      this.dataSource = dataSource;
      this.baseInvite = new MeetingInvite(
          "invite-1",
          "meeting-1",
          TOKEN,
          MeetingRole.PARTICIPANT,
          2,
          0,
          Instant.parse("2099-01-01T10:00:00Z"),
          null,
          Instant.parse("2099-01-01T09:00:00Z"),
          "tester",
          null,
          null,
          0L);
    }

    void reset() {
      failFindOnce = false;
      failOnSave = false;
      findAttempts = 0;
      saveAttempts = 0;
      findTransactionActiveFlags.clear();
      findTransactionResourceIds.clear();
    }

    void failOnceOnFindWithOptimisticLocking() {
      this.failFindOnce = true;
    }

    void failOnSaveWithOptimisticLocking() {
      this.failOnSave = true;
    }

    int findAttempts() {
      return findAttempts;
    }

    int saveAttempts() {
      return saveAttempts;
    }

    List<Boolean> findTransactionActiveFlags() {
      return List.copyOf(findTransactionActiveFlags);
    }

    List<Integer> findTransactionResourceIds() {
      return List.copyOf(findTransactionResourceIds);
    }

    @Override
    public MeetingInvite save(MeetingInvite invite) {
      saveAttempts++;
      if (failOnSave) {
        throw new ObjectOptimisticLockingFailureException(MeetingInvite.class, invite.id());
      }
      return invite;
    }

    @Override
    public List<MeetingInvite> saveAll(List<MeetingInvite> invites) {
      return invites;
    }

    @Override
    public Optional<MeetingInvite> findById(String id) {
      return Optional.empty();
    }

    @Override
    public Optional<MeetingInvite> findByToken(String token) {
      findAttempts++;
      findTransactionActiveFlags.add(TransactionSynchronizationManager.isActualTransactionActive());
      Object resource = TransactionSynchronizationManager.getResource(dataSource);
      findTransactionResourceIds.add(System.identityHashCode(resource));

      if (failFindOnce) {
        failFindOnce = false;
        throw new ObjectOptimisticLockingFailureException(MeetingInvite.class, baseInvite.id());
      }

      return Optional.of(baseInvite);
    }

    @Override
    public List<MeetingInvite> findByMeetingId(String meetingId, int page, int size) {
      return List.of();
    }

    @Override
    public Optional<MeetingInvite> findActiveByMeetingIdAndRecipientEmail(
        String meetingId,
        String recipientEmail,
        Instant now) {
      return Optional.empty();
    }

    @Override
    public Optional<MeetingInvite> findActiveByMeetingIdAndRecipientUserId(
        String meetingId,
        String recipientUserId,
        Instant now) {
      return Optional.empty();
    }

    @Override
    public long countByMeetingId(String meetingId) {
      return 0;
    }

    @Override
    public boolean existsByMeetingId(String meetingId) {
      return false;
    }
  }
}