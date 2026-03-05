package com.acme.jitsi.domains.meetings.listener;

import com.acme.jitsi.domains.meetings.event.MeetingBulkInviteCreatedEvent;
import com.acme.jitsi.domains.meetings.event.MeetingInviteCreatedEvent;
import com.acme.jitsi.domains.meetings.event.MeetingInviteRevokedEvent;
import com.acme.jitsi.domains.meetings.service.MeetingAuditLog;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class MeetingInviteAuditListener {

  private final MeetingAuditLog auditLog;
  private final MeterRegistry meterRegistry;

  public MeetingInviteAuditListener(MeetingAuditLog auditLog, MeterRegistry meterRegistry) {
    this.auditLog = auditLog;
    this.meterRegistry = meterRegistry;
  }

  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void onInviteCreated(MeetingInviteCreatedEvent event) {
    auditLog.record(
        "invite_create",
        event.roomId(),
        event.meetingId(),
        event.actorId(),
        event.traceId(),
        event.changedFields()
    );
    recordMetrics("invite_create");
  }

  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void onBulkInviteCreated(MeetingBulkInviteCreatedEvent event) {
    auditLog.record(
        "bulk_invite_create",
        event.roomId(),
        event.meetingId(),
        event.actorId(),
        event.traceId(),
        event.changedFields()
    );
    recordMetrics("bulk_invite_create");
  }

  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void onInviteRevoked(MeetingInviteRevokedEvent event) {
    auditLog.record(
        "invite_revoke",
        event.roomId(),
        event.meetingId(),
        event.actorId(),
        event.traceId(),
        event.changedFields()
    );
    recordMetrics("invite_revoke");
  }

  private void recordMetrics(String action) {
    meterRegistry.counter(
        "jitsi.audit.events.total",
        "domain",
        "meetings_invites",
        "action",
        action).increment();
    meterRegistry.counter(
        "jitsi.monitoring.events.total",
        "domain",
        "meetings_invites",
        "action",
        action).increment();
  }
}
