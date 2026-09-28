package com.acme.jitsi.domains.configsets.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.acme.jitsi.domains.auth.service.AuthAuditLog;
import com.acme.jitsi.domains.auth.service.AuthRefreshSecurityEvent;
import com.acme.jitsi.domains.configsets.event.ConfigSetCreatedEvent;
import com.acme.jitsi.domains.configsets.service.ConfigSetAuditLog;
import com.acme.jitsi.domains.meetings.event.MeetingCreatedEvent;
import com.acme.jitsi.domains.meetings.service.MeetingAuditLog;
import com.acme.jitsi.infrastructure.audit.AuditPublicationMaintenance;
import jakarta.persistence.EntityManager;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:audit-recovery;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.hibernate.ddl-auto=validate",
    "spring.flyway.enabled=true",
    "management.health.redis.enabled=false"
})
@Import(AuditRecoveryIntegrationTest.FailureConfig.class)
@Tag("integration")
class AuditRecoveryIntegrationTest {
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ApplicationEventPublisher publisher;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private IncompleteEventPublications incomplete;
    @Autowired private FlakyAuditLog flaky;
    @Autowired private FlakyMeetingAuditLog flakyMeeting;
    @Autowired private FlakyAuthAuditLog flakyAuth;
    @Autowired private AuditPublicationMaintenance maintenance;
    @Autowired private ConfigSetAuditListener listener;

    @Test
    void crashAfterAuditWriteIsRecoveredWithoutDuplicateRows() throws Exception {
        String id = UUID.randomUUID().toString();
        var event = new ConfigSetCreatedEvent(id, "actor", "trace-" + id, "name", "", "new config");
        flaky.fail.set(true);
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> publisher.publishEvent(event));
        await(() -> !flaky.fail.get());
        // Wait for the native listener failure/transaction to finish before retrying.
        Thread.sleep(200);
        jdbc.update("UPDATE event_publication SET publication_date = TIMESTAMP '2020-01-01 00:00:00' WHERE serialized_event LIKE ?", "%" + id + "%");
        maintenance.recover();
        await(() -> count(id) >= 1 && pending(id) == 0);
        incomplete.resubmitIncompletePublications(publication -> publication.getEvent().equals(event));
        listener.onCreated(event);
        Thread.sleep(200);
        assertThat(count(id)).isEqualTo(1);
        jdbc.update("UPDATE event_publication SET completion_date = TIMESTAMP '2020-01-01 00:00:00' WHERE serialized_event LIKE ?", "%" + id + "%");
        maintenance.pruneCompleted();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM event_publication WHERE serialized_event LIKE ?",
                Integer.class, "%" + id + "%")).isZero();
        assertThat(count(id)).isEqualTo(1);
    }

    @Test
    void meetingAuditWriteRollsBackAndScheduledRecoveryWritesExactlyOnce() throws Exception {
        String id = UUID.randomUUID().toString();
        var event = new MeetingCreatedEvent("room", id, "actor", id, "title");
        flakyMeeting.failure.arm();
        publish(event);
        await(flakyMeeting.failure.rolledBack::get);
        assertThat(auditCount("meeting_audit_events", id)).isZero();
        assertThat(pending(id)).isEqualTo(1);

        agePublication(id);
        maintenance.recover();
        await(() -> auditCount("meeting_audit_events", id) == 1 && pending(id) == 0);
        maintenance.recover();
        incomplete.resubmitIncompletePublications(publication -> publication.getEvent().equals(event));
        assertThat(auditCount("meeting_audit_events", id)).isEqualTo(1);
        assertThat(publicationCount(id)).isEqualTo(1);
    }

    @Test
    void authAuditWriteRollsBackAndScheduledRecoveryWritesExactlyOnce() throws Exception {
        String id = UUID.randomUUID().toString();
        var event = new AuthRefreshSecurityEvent("REFRESH_REPLAY_DETECTED", "AUTH_REFRESH_REUSED",
                "token-" + id, "subject", "meeting", id, Instant.now());
        flakyAuth.failure.arm();
        publish(event);
        await(flakyAuth.failure.rolledBack::get);
        assertThat(auditCount("auth_audit_events", id)).isZero();
        assertThat(pending(id)).isEqualTo(1);

        agePublication(id);
        maintenance.recover();
        await(() -> auditCount("auth_audit_events", id) == 1 && pending(id) == 0);
        maintenance.recover();
        incomplete.resubmitIncompletePublications(publication -> publication.getEvent().equals(event));
        assertThat(auditCount("auth_audit_events", id)).isEqualTo(1);
        assertThat(publicationCount(id)).isEqualTo(1);
    }

    @Test
    void completedCleanupRetainsPendingPublicationsAndAuditRows() throws Exception {
        String completedId = UUID.randomUUID().toString();
        publish(new ConfigSetCreatedEvent(completedId, "actor", completedId, "name", "", "new config"));
        await(() -> count(completedId) == 1 && pending(completedId) == 0);

        String pendingId = UUID.randomUUID().toString();
        flakyMeeting.failure.arm();
        publish(new MeetingCreatedEvent("room", pendingId, "actor", pendingId, "title"));
        await(flakyMeeting.failure.rolledBack::get);
        agePublication(completedId);
        agePublication(pendingId);
        jdbc.update("UPDATE event_publication SET completion_date = TIMESTAMP '2020-01-01 00:00:00' WHERE serialized_event LIKE ?",
                "%" + completedId + "%");

        maintenance.pruneCompleted();

        assertThat(publicationCount(completedId)).isZero();
        assertThat(count(completedId)).isEqualTo(1);
        assertThat(publicationCount(pendingId)).isEqualTo(1);
        assertThat(pending(pendingId)).isEqualTo(1);
        assertThat(auditCount("meeting_audit_events", pendingId)).isZero();
        maintenance.recover();
        await(() -> auditCount("meeting_audit_events", pendingId) == 1 && pending(pendingId) == 0);
    }

    private void publish(Object event) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> publisher.publishEvent(event));
    }

    private void agePublication(String id) {
        jdbc.update("UPDATE event_publication SET publication_date = TIMESTAMP '2020-01-01 00:00:00' WHERE serialized_event LIKE ?",
                "%" + id + "%");
    }

    private int auditCount(String table, String traceId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE trace_id = ?", Integer.class, traceId);
    }

    private int publicationCount(String id) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM event_publication WHERE serialized_event LIKE ?",
                Integer.class, "%" + id + "%");
    }

    private int count(String id) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM config_set_audit_events WHERE config_set_id = ?", Integer.class, id);
    }

    private int pending(String id) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM event_publication WHERE completion_date IS NULL AND serialized_event LIKE ?",
                Integer.class, "%" + id + "%");
    }

    private static void await(BooleanSupplier ready) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (!ready.getAsBoolean() && System.nanoTime() < deadline) { Thread.sleep(25); }
        assertThat(ready.getAsBoolean()).isTrue();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FailureConfig {
        @Bean @Primary
        FlakyAuditLog flakyAuditLog(JpaConfigSetAuditLog delegate) { return new FlakyAuditLog(delegate); }

        @Bean @Primary
        FlakyMeetingAuditLog flakyMeetingAuditLog(@Qualifier("jpaMeetingAuditLog") MeetingAuditLog delegate,
                EntityManager entityManager) {
            return new FlakyMeetingAuditLog(delegate, entityManager);
        }

        @Bean @Primary
        FlakyAuthAuditLog flakyAuthAuditLog(@Qualifier("jpaAuthAuditLog") AuthAuditLog delegate,
                EntityManager entityManager) {
            return new FlakyAuthAuditLog(delegate, entityManager);
        }
    }

    static class FlakyAuditLog implements ConfigSetAuditLog {
        private final ConfigSetAuditLog delegate;
        final AtomicBoolean fail = new AtomicBoolean();
        FlakyAuditLog(ConfigSetAuditLog delegate) { this.delegate = delegate; }
        @Override
        public void record(String eventType, String configSetId, String actorId, String traceId,
                String changedFields, String oldValues, String newValues) {
            delegate.record(eventType, configSetId, actorId, traceId, changedFields, oldValues, newValues);
            if (fail.compareAndSet(true, false)) { throw new IllegalStateException("simulated crash after audit write"); }
        }
    }

    static class FlakyMeetingAuditLog implements MeetingAuditLog {
        private final MeetingAuditLog delegate;
        final FailureAfterWrite failure;
        FlakyMeetingAuditLog(MeetingAuditLog delegate, EntityManager entityManager) {
            this.delegate = delegate;
            this.failure = new FailureAfterWrite(entityManager);
        }
        @Override
        public void record(String actionType, String roomId, String meetingId, String actorId,
                String traceId, String changedFields, String subjectId) {
            delegate.record(actionType, roomId, meetingId, actorId, traceId, changedFields, subjectId);
            failure.afterWrite();
        }
    }

    static class FlakyAuthAuditLog implements AuthAuditLog {
        private final AuthAuditLog delegate;
        final FailureAfterWrite failure;
        FlakyAuthAuditLog(AuthAuditLog delegate, EntityManager entityManager) {
            this.delegate = delegate;
            this.failure = new FailureAfterWrite(entityManager);
        }
        @Override
        public void record(String eventType, String actorId, String subjectId, String meetingId,
                String tokenId, String errorCode, String traceId, String tenantId, String clientContext) {
            delegate.record(eventType, actorId, subjectId, meetingId, tokenId, errorCode, traceId, tenantId, clientContext);
            failure.afterWrite();
        }
    }

    static class FailureAfterWrite {
        private final EntityManager entityManager;
        private final AtomicBoolean fail = new AtomicBoolean();
        final AtomicBoolean rolledBack = new AtomicBoolean();
        FailureAfterWrite(EntityManager entityManager) { this.entityManager = entityManager; }
        void arm() {
            rolledBack.set(false);
            fail.set(true);
        }
        void afterWrite() {
            if (!fail.compareAndSet(true, false)) { return; }
            // Force the insert to reach the database before simulating the process failure.
            entityManager.flush();
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    rolledBack.set(status == STATUS_ROLLED_BACK);
                }
            });
            throw new IllegalStateException("simulated crash after flushed audit write");
        }
    }
}
