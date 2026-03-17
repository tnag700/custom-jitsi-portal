package com.acme.jitsi.domains.meetings.listener;

import com.acme.jitsi.domains.meetings.event.MeetingJoinObservedEvent;
import com.acme.jitsi.domains.meetings.service.MeetingAuditLog;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import com.acme.jitsi.shared.observability.PhaseOneMonitoringMetrics;

@Component
public class MeetingJoinObservabilityListener {

  private final MeetingAuditLog meetingAuditLog;
  private final MeterRegistry meterRegistry;

  public MeetingJoinObservabilityListener(MeetingAuditLog meetingAuditLog, MeterRegistry meterRegistry) {
    this.meetingAuditLog = meetingAuditLog;
    this.meterRegistry = meterRegistry;
    PhaseOneMonitoringMetrics.registerJoinMeters(meterRegistry);
  }

  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void onMeetingJoinObserved(MeetingJoinObservedEvent event) {
    boolean success = "MEETING_JOIN_SUCCEEDED".equals(event.eventType());
    if (event.roomId() != null && !event.roomId().isBlank()) {
      meetingAuditLog.record(
          success ? "join_success" : "join_failed",
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
      success ? "join_success" : "join_failed").increment();
    meterRegistry.counter(
        "jitsi.monitoring.events.total",
        "domain",
        "meetings",
        "action",
      success ? "join_success" : "join_failed").increment();

    String result = PhaseOneMonitoringMetrics.normalizeResult(event.result(), !success);
    meterRegistry.counter(PhaseOneMonitoringMetrics.JOIN_ATTEMPTS_TOTAL).increment();
    if (success) {
      meterRegistry.counter(PhaseOneMonitoringMetrics.JOIN_SUCCESS_TOTAL).increment();
    } else {
      meterRegistry.counter(
        PhaseOneMonitoringMetrics.JOIN_FAILURE_TOTAL,
        PhaseOneMonitoringMetrics.TAG_REASON_CATEGORY,
        PhaseOneMonitoringMetrics.normalizeReasonCategory(event.reasonCategory()),
        PhaseOneMonitoringMetrics.TAG_ERROR_CODE,
        PhaseOneMonitoringMetrics.normalizeErrorCode(event.errorCode())).increment();
    }
    meterRegistry.timer(
        PhaseOneMonitoringMetrics.JOIN_LATENCY,
        PhaseOneMonitoringMetrics.TAG_RESULT,
        result)
      .record(Duration.ofMillis(Math.max(event.durationMs(), 0L)));
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