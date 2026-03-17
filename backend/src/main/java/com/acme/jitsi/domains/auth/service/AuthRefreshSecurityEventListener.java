package com.acme.jitsi.domains.auth.service;

import com.acme.jitsi.shared.observability.PhaseOneMonitoringMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
class AuthRefreshSecurityEventListener {

  private static final Logger log = LoggerFactory.getLogger(AuthRefreshSecurityEventListener.class);
  private final AuthAuditLog authAuditLog;
  private final MeterRegistry meterRegistry;

  AuthRefreshSecurityEventListener(AuthAuditLog authAuditLog, MeterRegistry meterRegistry) {
    this.authAuditLog = authAuditLog;
    this.meterRegistry = meterRegistry;
    PhaseOneMonitoringMetrics.registerAuthRefreshMeters(meterRegistry);
  }

  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  void onAuthRefreshSecurityEvent(AuthRefreshSecurityEvent event) {
    boolean failure = isFailure(event);
    String result = failure ? "fail" : "success";

    authAuditLog.record(
        event.eventType(),
        event.subject(),
        event.subject(),
        event.meetingId(),
        event.tokenId(),
        event.errorCode(),
        event.traceId(),
        null,
        null);

    recordMetrics(event, failure);

    if (failure) {
      if (log.isWarnEnabled()) {
        log.warn(
            "auth_audit_event eventType={} result={} errorCode={} tokenId={} subjectId={} meetingId={} traceId={} occurredAt={}",
            event.eventType(),
            result,
            event.errorCode(),
            event.tokenId(),
            event.subject(),
            event.meetingId(),
            event.traceId(),
            event.occurredAt());
      }
      return;
    }

    if (log.isInfoEnabled()) {
      log.info(
          "auth_audit_event eventType={} result={} errorCode={} tokenId={} subjectId={} meetingId={} traceId={} occurredAt={}",
          event.eventType(),
          result,
          event.errorCode(),
          event.tokenId(),
          event.subject(),
          event.meetingId(),
          event.traceId(),
          event.occurredAt());
    }
  }

  private boolean isFailure(AuthRefreshSecurityEvent event) {
    return event.errorCode() != null && !event.errorCode().isBlank();
  }

  private void recordMetrics(AuthRefreshSecurityEvent event, boolean failure) {
    String eventType = PhaseOneMonitoringMetrics.normalizeEventType(event.eventType());
    meterRegistry.counter(
        "jitsi.audit.events.total",
        "domain",
        "auth",
        "action",
        eventType.toLowerCase()).increment();
    meterRegistry.counter(
        "jitsi.monitoring.events.total",
        "domain",
        "auth",
        "action",
        eventType.toLowerCase()).increment();
    meterRegistry.counter(
      PhaseOneMonitoringMetrics.AUTH_REFRESH_EVENTS_TOTAL,
      PhaseOneMonitoringMetrics.TAG_EVENT_TYPE,
      eventType,
      PhaseOneMonitoringMetrics.TAG_RESULT,
      failure ? "fail" : "success",
      PhaseOneMonitoringMetrics.TAG_ERROR_CODE,
      PhaseOneMonitoringMetrics.normalizeErrorCode(event.errorCode())).increment();
  }
}
