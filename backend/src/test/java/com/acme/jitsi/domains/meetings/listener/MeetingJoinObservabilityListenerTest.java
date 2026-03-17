package com.acme.jitsi.domains.meetings.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.acme.jitsi.domains.meetings.event.MeetingJoinObservedEvent;
import com.acme.jitsi.domains.meetings.service.MeetingAuditLog;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.core.instrument.search.Search;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class MeetingJoinObservabilityListenerTest {

  @Test
  void recordsCanonicalJoinFailureMetricsWithoutHighCardinalityTags() {
    MeetingAuditLog meetingAuditLog = mock(MeetingAuditLog.class);
    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    MeetingJoinObservabilityListener listener = new MeetingJoinObservabilityListener(meetingAuditLog, meterRegistry);

    try {
      listener.onMeetingJoinObserved(new MeetingJoinObservedEvent(
          "MEETING_JOIN_FAILED",
          "fail",
          "meeting-123",
          "room-456",
          "subject-789",
          "participant",
          "TOKEN_INVALID",
          "TOKEN",
          "trace-join-1",
          812,
          Instant.parse("2026-03-16T12:00:00Z")));

      assertThat(meterRegistry.get("jitsi.join.attempts.total").counter().count()).isEqualTo(1.0d);
        assertThat(meterRegistry.get("jitsi.join.success.total").counter().count()).isZero();
      assertThat(meterRegistry.get("jitsi.join.failure.total")
          .tag("reason_category", "TOKEN")
          .tag("error_code", "TOKEN_INVALID")
          .counter()
          .count()).isEqualTo(1.0d);
      assertThat(meterRegistry.get("jitsi.join.latency")
          .tag("result", "fail")
          .timer()
          .count()).isEqualTo(1L);

      Meter failureMeter = Search.in(meterRegistry)
          .name("jitsi.join.failure.total")
          .tag("reason_category", "TOKEN")
          .tag("error_code", "TOKEN_INVALID")
          .meter();
      assertThat(failureMeter).isNotNull();
      assertThat(failureMeter.getId().getTag("traceId")).isNull();
      assertThat(failureMeter.getId().getTag("subjectId")).isNull();
      assertThat(failureMeter.getId().getTag("meetingId")).isNull();
      assertThat(failureMeter.getId().getTag("roomId")).isNull();

      verify(meetingAuditLog).record(
          "join_failed",
          "room-456",
          "meeting-123",
          "subject-789",
          "trace-join-1",
          "result=fail,role=participant,errorCode=TOKEN_INVALID,reasonCategory=TOKEN,durationMs=812",
          "subject-789");
    } finally {
      meterRegistry.close();
    }
  }

  @Test
  void recordsCanonicalJoinSuccessMetricsAndLatencyTimer() {
    MeetingAuditLog meetingAuditLog = mock(MeetingAuditLog.class);
    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    MeetingJoinObservabilityListener listener = new MeetingJoinObservabilityListener(meetingAuditLog, meterRegistry);

    try {
      listener.onMeetingJoinObserved(new MeetingJoinObservedEvent(
          "MEETING_JOIN_SUCCEEDED",
          "success",
          "meeting-abc",
          "room-def",
          "subject-xyz",
          "moderator",
          null,
          null,
          "trace-join-2",
          245,
          Instant.parse("2026-03-16T12:00:10Z")));

      assertThat(meterRegistry.get("jitsi.join.attempts.total").counter().count()).isEqualTo(1.0d);
      assertThat(meterRegistry.get("jitsi.join.success.total").counter().count()).isEqualTo(1.0d);
      assertThat(Search.in(meterRegistry)
          .name("jitsi.join.failure.total")
          .tag("reason_category", "TOKEN")
          .tag("error_code", "TOKEN_INVALID")
          .meters()).isEmpty();
      assertThat(meterRegistry.get("jitsi.join.latency")
          .tag("result", "success")
          .timer()
          .totalTime(java.util.concurrent.TimeUnit.MILLISECONDS)).isEqualTo(245.0d);
    } finally {
      meterRegistry.close();
    }
  }

  @Test
  void normalizesUnexpectedJoinResultTagToCanonicalOutcome() {
    MeetingAuditLog meetingAuditLog = mock(MeetingAuditLog.class);
    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    MeetingJoinObservabilityListener listener = new MeetingJoinObservabilityListener(meetingAuditLog, meterRegistry);

    try {
      listener.onMeetingJoinObserved(new MeetingJoinObservedEvent(
          "MEETING_JOIN_FAILED",
          "timeout",
          "meeting-xyz",
          "room-xyz",
          "subject-xyz",
          "participant",
          "TOKEN_INVALID",
          "TOKEN",
          "trace-join-3",
          321,
          Instant.parse("2026-03-16T12:00:20Z")));

      assertThat(meterRegistry.get("jitsi.join.latency")
          .tag("result", "fail")
          .timer()
          .count()).isEqualTo(1L);
      assertThat(Search.in(meterRegistry)
          .name("jitsi.join.latency")
          .tag("result", "timeout")
          .meters()).isEmpty();
    } finally {
      meterRegistry.close();
    }
  }
}