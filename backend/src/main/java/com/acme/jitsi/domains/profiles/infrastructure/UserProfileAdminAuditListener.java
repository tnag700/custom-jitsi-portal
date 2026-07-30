package com.acme.jitsi.domains.profiles.infrastructure;

import com.acme.jitsi.domains.profiles.event.UserProfileAdminUpdatedEvent;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
class UserProfileAdminAuditListener {

  private static final Logger log = LoggerFactory.getLogger(UserProfileAdminAuditListener.class);

  private final MeterRegistry meterRegistry;

  UserProfileAdminAuditListener(MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  void handle(UserProfileAdminUpdatedEvent event) {
    log.info(
        "profile_admin_audit action=update actorId={} subjectId={} tenantId={} traceId={} changedFields={}",
        event.actorId(),
        event.subjectId(),
        event.tenantId(),
        event.traceId(),
        event.changedFields());
    meterRegistry.counter(
        "jitsi.audit.events.total",
        "domain",
        "profiles",
        "action",
        "admin-update").increment();
  }
}
