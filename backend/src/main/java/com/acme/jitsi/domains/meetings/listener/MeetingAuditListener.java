package com.acme.jitsi.domains.meetings.listener;

import com.acme.jitsi.domains.meetings.event.MeetingCanceledEvent;
import com.acme.jitsi.domains.meetings.event.MeetingCreatedEvent;
import com.acme.jitsi.domains.meetings.event.MeetingUpdatedEvent;
import com.acme.jitsi.domains.meetings.service.MeetingAuditLog;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class MeetingAuditListener {

  private final MeetingAuditLog meetingAuditLog;
  private final MeterRegistry meterRegistry;

  public MeetingAuditListener(MeetingAuditLog meetingAuditLog, MeterRegistry meterRegistry) {
    this.meetingAuditLog = meetingAuditLog;
    this.meterRegistry = meterRegistry;
  }

  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void handleMeetingCreatedEvent(MeetingCreatedEvent event) {
    meetingAuditLog.record(
        "create",
        event.roomId(),
        event.meetingId(),
        event.actorId(),
        event.traceId(),
        event.changedFields());
    recordMetrics("create");
  }

  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void handleMeetingUpdatedEvent(MeetingUpdatedEvent event) {
    meetingAuditLog.record(
        "update",
        event.roomId(),
        event.meetingId(),
        event.actorId(),
        event.traceId(),
        event.changedFields());
    recordMetrics("update");
  }

  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void handleMeetingCanceledEvent(MeetingCanceledEvent event) {
    meetingAuditLog.record(
        "cancel",
        event.roomId(),
        event.meetingId(),
        event.actorId(),
        event.traceId(),
        event.changedFields());
    recordMetrics("cancel");
  }

  private void recordMetrics(String action) {
    meterRegistry.counter(
        "jitsi.audit.events.total",
        "domain",
        "meetings",
        "action",
        action).increment();
    meterRegistry.counter(
        "jitsi.monitoring.events.total",
        "domain",
        "meetings",
        "action",
        action).increment();
  }
}
