package com.acme.jitsi.domains.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.acme.jitsi.domains.auth.service.RefreshTokenStore;
import com.acme.jitsi.support.PostgresRedisContainerIntegrationTestSupport;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

@SpringBootTest(properties = "app.auth.refresh.atomic-store=database")
@Tag("integration")
@Tag("container")
class DatabaseRefreshTokenStorePostgresIntegrationTest
    extends PostgresRedisContainerIntegrationTestSupport {

  @Autowired
  private JdbcTemplate jdbcTemplate;

  @Autowired
  private PlatformTransactionManager transactionManager;

  @BeforeEach
  void setUp() {
    jdbcTemplate.execute("DELETE FROM refresh_token_states");
  }

  @Test
  void postgresSerializesTwoConcurrentRotationsAndPersistsOnlyOneSuccessor() throws Exception {
    DatabaseRefreshTokenStore setupStore = newStore();
    setupStore.createIfAbsent(activeState("postgres-current"));

    CyclicBarrier startBarrier = new CyclicBarrier(2);
    try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
      Future<RefreshTokenStore.ConsumeResult> first = executor.submit(
          () -> rotateAfterBarrier(newStore(), "postgres-successor-a", startBarrier));
      Future<RefreshTokenStore.ConsumeResult> second = executor.submit(
          () -> rotateAfterBarrier(newStore(), "postgres-successor-b", startBarrier));

      List<RefreshTokenStore.ConsumeStatus> statuses = List.of(
          first.get(10, TimeUnit.SECONDS).status(),
          second.get(10, TimeUnit.SECONDS).status());

      assertThat(statuses).containsExactlyInAnyOrder(
          RefreshTokenStore.ConsumeStatus.CONSUMED,
          RefreshTokenStore.ConsumeStatus.USED);
    }

    assertThat(jdbcTemplate.queryForObject(
        "SELECT status FROM refresh_token_states WHERE token_id = 'postgres-current'",
        String.class)).isEqualTo("USED");
    assertThat(jdbcTemplate.queryForObject(
        """
            SELECT COUNT(*)
            FROM refresh_token_states
            WHERE token_id IN ('postgres-successor-a', 'postgres-successor-b')
            """,
        Integer.class)).isEqualTo(1);
  }

  private RefreshTokenStore.ConsumeResult rotateAfterBarrier(
      DatabaseRefreshTokenStore store,
      String successorTokenId,
      CyclicBarrier startBarrier) throws Exception {
    startBarrier.await(10, TimeUnit.SECONDS);
    return store.rotate("postgres-current", activeState(successorTokenId));
  }

  private DatabaseRefreshTokenStore newStore() {
    return new DatabaseRefreshTokenStore(jdbcTemplate, transactionManager);
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
