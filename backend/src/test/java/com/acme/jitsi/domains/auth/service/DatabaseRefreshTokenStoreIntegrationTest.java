package com.acme.jitsi.domains.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.acme.jitsi.domains.auth.infrastructure.DatabaseRefreshTokenStore;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:testdb-refresh-token-store;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
      "spring.datasource.driver-class-name=org.h2.Driver",
      "spring.jpa.hibernate.ddl-auto=validate",
      "spring.flyway.enabled=true",
      "management.health.redis.enabled=false",
      "app.auth.refresh.atomic-store=database"
    })
class DatabaseRefreshTokenStoreIntegrationTest {

  @Autowired
  private JdbcTemplate jdbcTemplate;

  @Autowired
  private PlatformTransactionManager transactionManager;

  @Test
  void reuseRevokesTheAlreadyIssuedSuccessor() {
    var store = newStore();
    store.createIfAbsent(activeState("family-root"));
    store.rotate("family-root", activeState("family-child"));
    assertThat(store.rotate("family-root", activeState("other-child")).status())
        .isEqualTo(RefreshTokenStore.ConsumeStatus.USED);
    assertThat(store.consume("family-child").status()).isEqualTo(RefreshTokenStore.ConsumeStatus.REVOKED);
  }

  @Test
  void revokeIsOwnedAndNeverCreatesUnknownRows() {
    var store = newStore();
    store.createIfAbsent(activeState("owned-root"));
    assertThat(store.revoke("owned-root", "other-user")).isFalse();
    assertThat(store.revoke("unknown", "user-1")).isFalse();
    assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM refresh_token_states", Integer.class)).isEqualTo(1);
    assertThat(store.createIfAbsent(activeState("owned-root")).status()).isEqualTo(RefreshTokenStore.TokenStatus.ACTIVE);
    assertThat(store.revoke("owned-root", "user-1")).isTrue();
    assertThat(store.consume("owned-root").status()).isEqualTo(RefreshTokenStore.ConsumeStatus.REVOKED);
  }

  @Test
  void cleanupKeepsReplayMarkersUntilAbsoluteExpiry() {
    var store = newStore();
    store.createIfAbsent(activeState("used-root"));
    store.consume("used-root");
    jdbcTemplate.update("UPDATE refresh_token_states SET idle_expires_at = ? WHERE token_id = 'used-root'",
        java.sql.Timestamp.from(Instant.now().minusSeconds(60)));
    store.cleanupExpired();
    assertThat(store.createIfAbsent(activeState("used-root")).status()).isEqualTo(RefreshTokenStore.TokenStatus.USED);

    jdbcTemplate.update("UPDATE refresh_token_states SET absolute_expires_at = ? WHERE token_id = 'used-root'",
        java.sql.Timestamp.from(Instant.now().minusSeconds(60)));
    store.cleanupExpired();
    assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM refresh_token_states", Integer.class)).isZero();
  }

  @Test
  void rotationRechecksExpiryInsideItsTransaction() {
    var store = newStore();
    store.createIfAbsent(activeState("expired-during-issuance"));
    jdbcTemplate.update("UPDATE refresh_token_states SET idle_expires_at = ? WHERE token_id = 'expired-during-issuance'",
        java.sql.Timestamp.from(Instant.now().minusSeconds(1)));
    assertThat(store.rotate("expired-during-issuance", activeState("must-not-exist")).status())
        .isEqualTo(RefreshTokenStore.ConsumeStatus.MISSING);
    assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM refresh_token_states WHERE token_id = 'must-not-exist'", Integer.class))
        .isZero();
  }

  @Test
  void rootReplayAndChildRotationCannotLeaveAnActiveGrandchild() throws Exception {
    var store = newStore();
    store.createIfAbsent(activeState("root"));
    store.rotate("root", activeState("child"));
    var start = new java.util.concurrent.CyclicBarrier(2);
    try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
      var replay = executor.submit(() -> { start.await(); return newStore().consume("root"); });
      var rotate = executor.submit(() -> { start.await(); return newStore().rotate("child", activeState("grandchild")); });
      replay.get(5, java.util.concurrent.TimeUnit.SECONDS);
      rotate.get(5, java.util.concurrent.TimeUnit.SECONDS);
    }
    assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM refresh_token_states WHERE status = 'ACTIVE'", Integer.class)).isZero();
    assertThat(store.consume("child").status()).isEqualTo(RefreshTokenStore.ConsumeStatus.REVOKED);
  }

  @BeforeEach
  void setUp() {
    jdbcTemplate.execute("DELETE FROM refresh_token_states");
    jdbcTemplate.update(
        "UPDATE refresh_token_store_metadata SET accept_issued_after = ? WHERE singleton_id = 1",
        java.sql.Timestamp.from(Instant.EPOCH));
  }

  @Test
  void cutoverBoundaryPersistsAcrossRestartAndCannotMoveBackward() {
    Instant firstBoundary = Instant.parse("2026-08-13T12:00:01Z");
    enforceCutover(firstBoundary);

    assertThat(jdbcTemplate.<Instant>queryForObject(
        "SELECT accept_issued_after FROM refresh_token_store_metadata WHERE singleton_id = 1",
        (result, row) -> result.getTimestamp(1).toInstant())).isEqualTo(firstBoundary);

    assertThatThrownBy(() -> enforceCutover(firstBoundary.minusSeconds(1)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("cannot move backward");
    assertThat(jdbcTemplate.<Instant>queryForObject(
        "SELECT accept_issued_after FROM refresh_token_store_metadata WHERE singleton_id = 1",
        (result, row) -> result.getTimestamp(1).toInstant())).isEqualTo(firstBoundary);
  }

  @Test
  void controlledLaterCutoverAdvancesThePersistedBoundary() {
    enforceCutover(Instant.parse("2026-08-13T12:00:01Z"));
    Instant laterBoundary = Instant.parse("2026-08-13T13:00:01Z");

    enforceCutover(laterBoundary);

    assertThat(jdbcTemplate.<Instant>queryForObject(
        "SELECT accept_issued_after FROM refresh_token_store_metadata WHERE singleton_id = 1",
        (result, row) -> result.getTimestamp(1).toInstant())).isEqualTo(laterBoundary);
  }

  @Test
  void usedStateSurvivesAStoreRecreationAndCannotBeReactivated() {
    DatabaseRefreshTokenStore firstProcess = newStore();
    RefreshTokenStore.RefreshTokenState current = activeState("current-token");
    RefreshTokenStore.RefreshTokenState successor = activeState("successor-token");
    firstProcess.createIfAbsent(current);

    assertThat(firstProcess.rotate(current.tokenId(), successor).status())
        .isEqualTo(RefreshTokenStore.ConsumeStatus.CONSUMED);

    DatabaseRefreshTokenStore restartedProcess = newStore();
    RefreshTokenStore.RefreshTokenState persisted = restartedProcess.createIfAbsent(current);

    assertThat(persisted.status()).isEqualTo(RefreshTokenStore.TokenStatus.USED);
    assertThat(restartedProcess.rotate(current.tokenId(), activeState("other-successor")).status())
        .isEqualTo(RefreshTokenStore.ConsumeStatus.USED);
  }

  @Test
  void revokedMarkerSurvivesAStoreRecreationAndCannotBeReactivated() {
    DatabaseRefreshTokenStore firstProcess = newStore();
    firstProcess.createIfAbsent(activeState("revoked-token"));
    firstProcess.revoke("revoked-token", "user-1");

    DatabaseRefreshTokenStore restartedProcess = newStore();
    RefreshTokenStore.RefreshTokenState persisted =
        restartedProcess.createIfAbsent(activeState("revoked-token"));

    assertThat(persisted.status()).isEqualTo(RefreshTokenStore.TokenStatus.REVOKED);
    assertThat(restartedProcess.consume("revoked-token").status())
        .isEqualTo(RefreshTokenStore.ConsumeStatus.REVOKED);
  }

  @Test
  void concurrentRotationsCommitExactlyOneSuccessor() throws Exception {
    DatabaseRefreshTokenStore firstProcess = newStore();
    firstProcess.createIfAbsent(activeState("shared-current-token"));

    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);
    try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
      Future<RefreshTokenStore.ConsumeResult> first = executor.submit(
          () -> rotateAfterBarrier(newStore(), "successor-a", ready, start));
      Future<RefreshTokenStore.ConsumeResult> second = executor.submit(
          () -> rotateAfterBarrier(newStore(), "successor-b", ready, start));

      ready.await();
      start.countDown();

      assertThat(List.of(first.get().status(), second.get().status()))
          .containsExactlyInAnyOrder(
              RefreshTokenStore.ConsumeStatus.CONSUMED,
              RefreshTokenStore.ConsumeStatus.USED);
    }

    assertThat(jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM refresh_token_states WHERE token_id IN ('successor-a', 'successor-b')",
        Integer.class)).isEqualTo(1);
    assertThat(jdbcTemplate.queryForObject(
        "SELECT status FROM refresh_token_states WHERE token_id = 'shared-current-token'",
        String.class)).isEqualTo("REVOKED");
  }

  @Test
  void anExistingSuccessorNeverPartiallyConsumesTheCurrentToken() {
    DatabaseRefreshTokenStore store = newStore();
    store.createIfAbsent(activeState("collision-current"));
    store.createIfAbsent(activeState("collision-successor"));

    assertThatThrownBy(() -> store.rotate("collision-current", activeState("collision-successor")))
        .isInstanceOf(IllegalStateException.class);
    assertThat(store.createIfAbsent(activeState("collision-successor")).status())
        .isEqualTo(RefreshTokenStore.TokenStatus.ACTIVE);
    assertThat(store.createIfAbsent(activeState("collision-current")).status())
        .isEqualTo(RefreshTokenStore.TokenStatus.ACTIVE);
  }

  @Test
  void unavailableDatabaseFailsClosedWithServiceUnavailable() throws SQLException {
    DataSource unavailableDataSource = mock(DataSource.class);
    when(unavailableDataSource.getConnection()).thenThrow(new SQLException("database unavailable"));
    JdbcTemplate unavailableJdbc = new JdbcTemplate(unavailableDataSource);
    org.springframework.jdbc.datasource.DataSourceTransactionManager unavailableTransactions =
        new org.springframework.jdbc.datasource.DataSourceTransactionManager(unavailableDataSource);
    DatabaseRefreshTokenStore unavailableStore =
        new DatabaseRefreshTokenStore(unavailableJdbc, unavailableTransactions);

    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> unavailableStore.createIfAbsent(activeState("fail-closed-token")))
        .isInstanceOf(AuthTokenException.class)
        .satisfies(exception -> assertThat(((AuthTokenException) exception).status().value())
            .isEqualTo(503));
  }

  private RefreshTokenStore.ConsumeResult rotateAfterBarrier(
      DatabaseRefreshTokenStore store,
      String successorTokenId,
      CountDownLatch ready,
      CountDownLatch start) throws InterruptedException {
    ready.countDown();
    start.await();
    return store.rotate("shared-current-token", activeState(successorTokenId));
  }

  private DatabaseRefreshTokenStore newStore() {
    return new DatabaseRefreshTokenStore(jdbcTemplate, transactionManager);
  }

  private void enforceCutover(Instant boundary) {
    AuthRefreshProperties properties = new AuthRefreshProperties();
    properties.setAcceptIssuedAfter(boundary);
    new ProductionRefreshTokenCutoverGuard(
        properties,
        jdbcTemplate,
        new TransactionTemplate(transactionManager))
        .afterPropertiesSet();
  }

  private RefreshTokenStore.RefreshTokenState activeState(String tokenId) {
    Instant now = Instant.now();
    return new RefreshTokenStore.RefreshTokenState(
        tokenId,
        "user-1",
        "meeting-1",
        now.plusSeconds(7200),
        now.plusSeconds(3600),
        RefreshTokenStore.TokenStatus.ACTIVE);
  }
}
