package com.acme.jitsi.domains.meetings.listener;

import com.acme.jitsi.domains.meetings.event.MeetingParticipantAssignedEvent;
import com.acme.jitsi.domains.meetings.event.MeetingParticipantRemovedEvent;
import com.acme.jitsi.domains.meetings.event.MeetingParticipantRoleChangedEvent;
import com.acme.jitsi.domains.meetings.service.MeetingAuditLog;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class MeetingParticipantAuditListener {

  private final MeetingAuditLog auditLog;
  private final MeterRegistry meterRegistry;

  public MeetingParticipantAuditListener(MeetingAuditLog auditLog, MeterRegistry meterRegistry) {
    this.auditLog = auditLog;
    this.meterRegistry = meterRegistry;
  }

  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void onParticipantAssigned(MeetingParticipantAssignedEvent event) {
    auditLog.record(
        "assign",
        event.roomId(),
        event.meetingId(),
        event.actorId(),
        event.traceId(),
        event.changedFields(),
        event.subjectId()
    );
    recordMetrics("assign");
  }

  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void onParticipantRoleChanged(MeetingParticipantRoleChangedEvent event) {
    auditLog.record(
        "update",
        event.roomId(),
        event.meetingId(),
        event.actorId(),
        event.traceId(),
        event.changedFields(),
        event.subjectId()
    );
    recordMetrics("update");
  }

  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void onParticipantRemoved(MeetingParticipantRemovedEvent event) {
    auditLog.record(
        "unassign",
        event.roomId(),
        event.meetingId(),
        event.actorId(),
        event.traceId(),
        event.changedFields(),
        event.subjectId()
    );
    recordMetrics("unassign");
  }

  private void recordMetrics(String action) {
    meterRegistry.counter(
        "jitsi.audit.events.total",
        "domain",
        "meetings_participants",
        "action",
        action).increment();
    meterRegistry.counter(
        "jitsi.monitoring.events.total",
        "domain",
        "meetings_participants",
        "action",
        action).increment();
  }
}
