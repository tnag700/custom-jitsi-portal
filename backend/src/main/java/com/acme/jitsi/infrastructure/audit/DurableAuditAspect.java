package com.acme.jitsi.infrastructure.audit;

import java.time.Instant;
import java.util.UUID;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.events.core.EventPublicationRepository;
import org.springframework.modulith.events.core.EventSerializer;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionalApplicationListenerMethodAdapter;
import org.springframework.transaction.support.TransactionTemplate;

/** Commits a database audit row and its existing Modulith publication together. */
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class DurableAuditAspect {
    private final JdbcTemplate jdbc;
    private final EventSerializer serializer;
    private final EventPublicationRepository publications;
    private final TransactionTemplate transactions;

    public DurableAuditAspect(JdbcTemplate jdbc, EventSerializer serializer,
            EventPublicationRepository publications, PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.serializer = serializer;
        this.publications = publications;
        this.transactions = new TransactionTemplate(transactionManager);
        this.transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Around("execution(* com.acme.jitsi.domains.configsets.infrastructure.ConfigSetAuditListener.*(..))"
            + " || execution(* com.acme.jitsi.domains.meetings.listener.*AuditListener.*(..))"
            + " || execution(* com.acme.jitsi.domains.auth.service.AuthRefreshSecurityEventListener.*(..))")
    public Object recordOnce(ProceedingJoinPoint invocation) throws Throwable {
        var method = ((MethodSignature) invocation.getSignature()).getMethod();
        String listenerId = new TransactionalApplicationListenerMethodAdapter(
                "audit", method.getDeclaringClass(), method).getListenerId();
        String event = serializer.serialize(invocation.getArgs()[0]).toString();
        Throwable[] failure = {null};
        Object result = transactions.execute(status -> {
            var matching = jdbc.query("""
                    SELECT id, completion_date IS NOT NULL AS completed FROM event_publication
                    WHERE listener_id = ? AND serialized_event = ?
                    ORDER BY CASE WHEN completion_date IS NULL THEN 0 ELSE 1 END, publication_date
                    LIMIT 1 FOR UPDATE
                    """, (row, index) -> new Publication(row.getObject("id", UUID.class), row.getBoolean("completed")),
                    listenerId, event);
            if (!matching.isEmpty() && matching.getFirst().completed()) {
                return null;
            }
            try {
                Object response = invocation.proceed();
                if (!matching.isEmpty()) {
                    // The native registry's normal completion interceptor runs in a separate transaction.
                    // Complete here as well, before audit+publication commit, closing the crash window.
                    publications.markCompleted(matching.getFirst().id(), Instant.now());
                }
                return response;
            } catch (Throwable exception) {
                status.setRollbackOnly();
                failure[0] = exception;
                return null;
            }
        });
        if (failure[0] != null) {
            throw failure[0];
        }
        return result;
    }

    private record Publication(UUID id, boolean completed) { }
}
