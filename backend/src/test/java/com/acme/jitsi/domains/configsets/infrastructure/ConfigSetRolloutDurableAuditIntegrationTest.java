package com.acme.jitsi.domains.configsets.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.acme.jitsi.support.TestDomainModuleApplication;
import com.acme.jitsi.domains.configsets.service.ConfigSet;
import com.acme.jitsi.domains.configsets.service.ConfigSetAuditLog;
import com.acme.jitsi.domains.configsets.service.ConfigSetEnvironmentType;
import com.acme.jitsi.domains.configsets.service.ConfigSetRepository;
import com.acme.jitsi.domains.configsets.service.ConfigSetStatus;
import com.acme.jitsi.domains.configsets.usecase.RolloutConfigSetCommand;
import com.acme.jitsi.domains.configsets.usecase.RolloutConfigSetUseCase;
import com.acme.jitsi.support.PostgresRedisContainerIntegrationTestSupport;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(
  classes = TestDomainModuleApplication.class,
  properties = {
    "spring.main.web-application-type=none",
    "spring.main.allow-bean-definition-overriding=true",
    "APP_CONFIG_SETS_ENCRYPTION_KEY=0123456789ABCDEF0123456789ABCDEF",
    "app.meetings.token.signing-secret=01234567890123456789012345678901",
    "app.meetings.token.issuer=https://portal.example.test",
    "app.meetings.token.audience=jitsi-meet",
    "app.meetings.token.algorithm=HS256",
    "app.meetings.token.ttl-minutes=20",
    "app.meetings.token.role-claim-name=role",
    "app.meetings.token.join-url-template=https://meet.example/%s#jwt=%s",
    "app.auth.refresh.idle-ttl-minutes=60",
    "app.security.jwt-contour.issuer=https://portal.example.test",
    "app.security.jwt-contour.audience=jitsi-meet",
    "app.security.jwt-contour.role-claim=role",
    "app.security.jwt-contour.algorithm=HS256",
    "app.security.jwt-contour.access-ttl-minutes=20",
    "app.security.jwt-contour.refresh-ttl-minutes=60",
    "spring.flyway.enabled=true",
    "spring.modulith.events.jdbc.schema-initialization.enabled=false"
  })
@Import(ConfigSetRolloutDurableAuditIntegrationTest.DurableAuditTestConfiguration.class)
@Tag("integration")
@Tag("container")
class ConfigSetRolloutDurableAuditIntegrationTest extends PostgresRedisContainerIntegrationTestSupport {

  @Autowired
  private ConfigSetRepository configSetRepository;

  @Autowired
  private RolloutConfigSetUseCase rolloutConfigSetUseCase;

  @Autowired
  private ConfigSetAuditEventJpaRepository auditEventJpaRepository;

  @Autowired
  private ConfigSetRolloutAuditPublicationMonitor publicationMonitor;

  @Autowired
  private FlakyConfigSetAuditLog flakyAuditLog;

  @Autowired
  private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void cleanPilotArtifacts() {
    jdbcTemplate.update(
        "DELETE FROM event_publication WHERE listener_id = ?",
        ConfigSetAuditListener.DURABLE_ROLLOUT_COMPLETED_LISTENER_ID);
    flakyAuditLog.reset();
  }

  @Test
  void rolloutCompletedAuditRemainsRecoverableAfterListenerFailure() throws Exception {
    String configSetId = UUID.randomUUID().toString();
    String traceId = "trace-" + UUID.randomUUID();
    configSetRepository.save(draft(configSetId));
    flakyAuditLog.failNextRolloutCompletion();

    rolloutConfigSetUseCase.execute(new RolloutConfigSetCommand(configSetId, "tenant-1", "actor-1", traceId));

    waitUntil(() -> publicationMonitor.findIncompletePilotPublications().size() == 1, Duration.ofSeconds(5));

    var incompletePublications = publicationMonitor.findIncompletePilotPublications();
    assertThat(incompletePublications)
        .singleElement()
        .satisfies(publication -> {
          assertThat(publication.listenerId())
              .isEqualTo(ConfigSetAuditListener.DURABLE_ROLLOUT_COMPLETED_LISTENER_ID);
          assertThat(publication.eventType())
              .isEqualTo("com.acme.jitsi.domains.configsets.event.ConfigSetRolloutCompletedEvent");
          assertThat(publication.status().name()).isIn("FAILED", "RESUBMITTED", "PROCESSING", "PUBLISHED");
          assertThat(publication.completionAttempts()).isGreaterThanOrEqualTo(1);
        });
        assertThat(auditEventJpaRepository.countByConfigSetIdAndEventType(
          configSetId,
          "CONFIG_SET_ROLLOUT_COMPLETED")).isZero();

    publicationMonitor.resubmitIncompletePilotPublications();

    waitUntil(
          () -> auditEventJpaRepository.countByConfigSetIdAndEventType(
            configSetId,
            "CONFIG_SET_ROLLOUT_COMPLETED") == 1,
        Duration.ofSeconds(5));
    waitUntil(() -> publicationMonitor.findIncompletePilotPublications().isEmpty(), Duration.ofSeconds(5));

        assertThat(auditEventJpaRepository.existsByConfigSetIdAndEventTypeAndTraceId(
          configSetId,
          "CONFIG_SET_ROLLOUT_COMPLETED",
          traceId)).isTrue();
  }

  private static ConfigSet draft(String configSetId) {
    Instant now = Instant.parse("2026-03-13T00:00:00Z");
    return new ConfigSet(
        configSetId,
        "Pilot Config",
        "tenant-1",
        ConfigSetEnvironmentType.DEV,
      "https://portal.example.test",
        "jitsi-meet",
        "HS256",
        "role",
        "01234567890123456789012345678901",
        null,
        20,
        60,
      "https://meet.example.test/v1",
        ConfigSetStatus.DRAFT,
        now,
        now);
  }

  private static void waitUntil(BooleanSupplier condition, Duration timeout) throws InterruptedException {
    long deadline = System.nanoTime() + timeout.toNanos();
    while (System.nanoTime() < deadline) {
      if (condition.getAsBoolean()) {
        return;
      }
      Thread.sleep(100);
    }

    assertThat(condition.getAsBoolean()).isTrue();
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class DurableAuditTestConfiguration {

    @Bean
    @Primary
    FlakyConfigSetAuditLog configSetAuditLog(ConfigSetAuditEventJpaRepository repository, Clock clock) {
      return new FlakyConfigSetAuditLog(repository, clock);
    }
  }

  static final class FlakyConfigSetAuditLog implements ConfigSetAuditLog {

    private final ConfigSetAuditEventJpaRepository repository;
    private final Clock clock;
    private final AtomicBoolean failNextRolloutCompletion = new AtomicBoolean(false);

    FlakyConfigSetAuditLog(ConfigSetAuditEventJpaRepository repository, Clock clock) {
      this.repository = repository;
      this.clock = clock;
    }

    void failNextRolloutCompletion() {
      failNextRolloutCompletion.set(true);
    }

    void reset() {
      failNextRolloutCompletion.set(false);
    }

    @Override
    public void record(
        String eventType,
        String configSetId,
        String actorId,
        String traceId,
        String changedFields,
        String oldValues,
        String newValues) {
      if ("CONFIG_SET_ROLLOUT_COMPLETED".equals(eventType)
          && failNextRolloutCompletion.compareAndSet(true, false)) {
        throw new IllegalStateException("Simulated rollout audit persistence failure");
      }

      repository.save(new ConfigSetAuditEventEntity(
          UUID.randomUUID().toString(),
          configSetId,
          eventType,
          actorId,
          traceId,
          changedFields,
          oldValues,
          newValues,
          Instant.now(clock)));
    }
  }
}