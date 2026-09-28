package com.acme.jitsi.domains.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Tag("integration")
@Tag("container")
@Testcontainers(disabledWithoutDocker = true)
class RedisRefreshTokenStoreIntegrationTest {

  @Container
  @SuppressWarnings("resource")
  private static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine")
      .withExposedPorts(6379);

  private LettuceConnectionFactory connectionFactory;
  private StringRedisTemplate redis;
  private RedisRefreshTokenStore store;

  @BeforeEach
  void setUp() {
    connectionFactory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
    connectionFactory.afterPropertiesSet();
    redis = new StringRedisTemplate(connectionFactory);
    try (var connection = connectionFactory.getConnection()) {
      connection.serverCommands().flushDb();
    }
    var beans = new StaticListableBeanFactory();
    beans.addBean("redis", redis);
    store = new RedisRefreshTokenStore(beans.getBeanProvider(StringRedisTemplate.class));
  }

  @AfterEach
  void tearDown() {
    if (connectionFactory != null) {
      connectionFactory.destroy();
    }
  }

  @Test
  void reuseOfIdleExpiredAncestorRevokesEveryDescendant() {
    store.createIfAbsent(active("root"));
    assertThat(store.rotate("root", active("child")).status()).isEqualTo(RefreshTokenStore.ConsumeStatus.CONSUMED);
    assertThat(store.rotate("child", active("grandchild")).status()).isEqualTo(RefreshTokenStore.ConsumeStatus.CONSUMED);
    redis.opsForHash().put("auth:refresh:child", "idleExpiresAt", "1");

    assertThat(store.consume("child").status()).isEqualTo(RefreshTokenStore.ConsumeStatus.USED);
    assertThat(store.consume("grandchild").status()).isEqualTo(RefreshTokenStore.ConsumeStatus.REVOKED);
    assertThat(store.consume("child").status()).isEqualTo(RefreshTokenStore.ConsumeStatus.REVOKED);
    assertThat(redis.getExpire("auth:refresh:root")).isPositive();
  }

  @Test
  void revocationRequiresOwnershipAndRevokesTheFamilyWithoutPlaceholders() {
    store.createIfAbsent(active("root"));
    store.rotate("root", active("child"));
    assertThat(store.revoke("child", "stranger")).isFalse();
    assertThat(store.revoke("unknown", "owner")).isFalse();
    assertThat(redis.hasKey("auth:refresh:unknown")).isFalse();
    assertThat(store.rotate("child", active("grandchild")).status()).isEqualTo(RefreshTokenStore.ConsumeStatus.CONSUMED);

    assertThat(store.revoke("child", "owner")).isTrue();
    assertThat(store.revoke("child", "owner")).isTrue();
    assertThat(store.consume("grandchild").status()).isEqualTo(RefreshTokenStore.ConsumeStatus.REVOKED);
  }

  @Test
  void expiredCurrentStateCannotCreateASuccessor() {
    store.createIfAbsent(active("root"));
    redis.opsForHash().put("auth:refresh:root", "idleExpiresAt", "1");
    assertThat(store.rotate("root", active("child")).status()).isEqualTo(RefreshTokenStore.ConsumeStatus.MISSING);
    assertThat(redis.hasKey("auth:refresh:child")).isFalse();
  }

  @Test
  void unknownExpiredStateIsNotStoredAndKnownUsedStateStillDetectsReplay() {
    var expired = new RefreshTokenStore.RefreshTokenState("expired", "owner", "meeting",
        Instant.now().plusSeconds(3600), Instant.now().minusSeconds(1), RefreshTokenStore.TokenStatus.ACTIVE);
    assertThat(store.createIfAbsent(expired)).isEqualTo(expired);
    assertThat(redis.hasKey("auth:refresh:expired")).isFalse();

    var root = active("root");
    store.createIfAbsent(root);
    store.rotate("root", active("child"));
    redis.opsForHash().put("auth:refresh:root", "idleExpiresAt", "1");
    assertThat(store.createIfAbsent(root).status()).isEqualTo(RefreshTokenStore.TokenStatus.USED);
    assertThat(store.consume("root").status()).isEqualTo(RefreshTokenStore.ConsumeStatus.USED);
    assertThat(store.consume("child").status()).isEqualTo(RefreshTokenStore.ConsumeStatus.REVOKED);
  }

  @Test
  void legacyAndMissingFamilyStateAreRefused() {
    store.createIfAbsent(active("legacy"));
    redis.opsForHash().delete("auth:refresh:legacy", "familyId");
    assertThat(store.consume("legacy").status()).isEqualTo(RefreshTokenStore.ConsumeStatus.REVOKED);
    store.createIfAbsent(active("root"));
    store.rotate("root", active("child"));
    redis.delete("auth:refresh:root");
    assertThat(store.consume("child").status()).isEqualTo(RefreshTokenStore.ConsumeStatus.REVOKED);
  }

  @Test
  void successorCannotExtendTheFamilyLifetimeOrChangeOwnership() {
    var root = active("root");
    store.createIfAbsent(root);
    var wrongOwner = new RefreshTokenStore.RefreshTokenState("wrong", "stranger", "meeting",
        root.absoluteExpiresAt(), root.idleExpiresAt(), RefreshTokenStore.TokenStatus.ACTIVE);
    assertThatThrownBy(() -> store.rotate("root", wrongOwner)).isInstanceOf(IllegalArgumentException.class);
    assertThat(redis.hasKey("auth:refresh:wrong")).isFalse();
    var successor = new RefreshTokenStore.RefreshTokenState("child", "owner", "meeting",
        root.absoluteExpiresAt().plusSeconds(3600), root.absoluteExpiresAt().plusSeconds(1800),
        RefreshTokenStore.TokenStatus.REVOKED);
    assertThat(store.rotate("root", successor).status()).isEqualTo(RefreshTokenStore.ConsumeStatus.CONSUMED);
    var persisted = store.createIfAbsent(successor);
    assertThat(persisted.absoluteExpiresAt().toEpochMilli()).isEqualTo(root.absoluteExpiresAt().toEpochMilli());
    assertThat(persisted.idleExpiresAt().toEpochMilli()).isEqualTo(root.absoluteExpiresAt().toEpochMilli());
    assertThat(persisted.status()).isEqualTo(RefreshTokenStore.TokenStatus.ACTIVE);
  }

  @Test
  void successorCollisionLeavesBothExistingTokensUsable() {
    store.createIfAbsent(active("root"));
    store.createIfAbsent(active("existing"));
    assertThatThrownBy(() -> store.rotate("root", active("existing")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("A refresh successor token ID already exists.");
    assertThat(store.consume("root").status()).isEqualTo(RefreshTokenStore.ConsumeStatus.CONSUMED);
    assertThat(store.consume("existing").status()).isEqualTo(RefreshTokenStore.ConsumeStatus.CONSUMED);
  }

  @Test
  void concurrentRotationsCreateOnlyOneSuccessorAndRevokeItOnReuse() throws Exception {
    store.createIfAbsent(active("root"));
    CyclicBarrier barrier = new CyclicBarrier(2);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var left = executor.submit(() -> { barrier.await(); return store.rotate("root", active("left")); });
      var right = executor.submit(() -> { barrier.await(); return store.rotate("root", active("right")); });
      assertThat(List.of(left.get().status(), right.get().status())).containsExactlyInAnyOrder(
          RefreshTokenStore.ConsumeStatus.CONSUMED, RefreshTokenStore.ConsumeStatus.USED);
    }
    assertThat(redis.hasKey("auth:refresh:left") ^ redis.hasKey("auth:refresh:right")).isTrue();
    String successor = Boolean.TRUE.equals(redis.hasKey("auth:refresh:left")) ? "left" : "right";
    assertThat(store.consume(successor).status()).isEqualTo(RefreshTokenStore.ConsumeStatus.REVOKED);
  }

  private RefreshTokenStore.RefreshTokenState active(String tokenId) {
    Instant now = Instant.now();
    return new RefreshTokenStore.RefreshTokenState(tokenId, "owner", "meeting", now.plusSeconds(3600),
        now.plusSeconds(1800), RefreshTokenStore.TokenStatus.ACTIVE);
  }
}
