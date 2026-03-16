package com.acme.jitsi.domains.meetings.listener;

import com.acme.jitsi.domains.meetings.event.MeetingJoinObservedEvent;
import com.acme.jitsi.domains.meetings.service.MeetingAuditLog;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class MeetingJoinObservabilityListener {

  private final MeetingAuditLog meetingAuditLog;
  private final MeterRegistry meterRegistry;

  public MeetingJoinObservabilityListener(MeetingAuditLog meetingAuditLog, MeterRegistry meterRegistry) {
    this.meetingAuditLog = meetingAuditLog;
    this.meterRegistry = meterRegistry;
  }

  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void onMeetingJoinObserved(MeetingJoinObservedEvent event) {
    if (event.roomId() != null && !event.roomId().isBlank()) {
      meetingAuditLog.record(
          "MEETING_JOIN_SUCCEEDED".equals(event.eventType()) ? "join_success" : "join_failed",
          event.roomId(),
          event.meetingId(),
          event.subjectId(),
          event.traceId(),
          buildChangedFields(event),
          event.subjectId());
    }

    meterRegistry.counter(
        "jitsi.audit.events.total",
        "domain",
        "meetings",
        "action",
        "MEETING_JOIN_SUCCEEDED".equals(event.eventType()) ? "join_success" : "join_failed").increment();
    meterRegistry.counter(
        "jitsi.monitoring.events.total",
        "domain",
        "meetings",
        "action",
        "MEETING_JOIN_SUCCEEDED".equals(event.eventType()) ? "join_success" : "join_failed").increment();
  }

  private String buildChangedFields(MeetingJoinObservedEvent event) {
    return "result=%s,role=%s,errorCode=%s,reasonCategory=%s,durationMs=%s".formatted(
        event.result(),
        event.role(),
        event.errorCode(),
        event.reasonCategory(),
        event.durationMs());
  }
}