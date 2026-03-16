package com.acme.jitsi.domains.meetings.service;

import com.acme.jitsi.domains.meetings.event.MeetingJoinObservedEvent;
import com.acme.jitsi.shared.ErrorCode;
import java.time.Clock;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
public class MeetingJoinObservabilityPublisher {

  private final ApplicationEventPublisher eventPublisher;
  private final MeetingService meetingService;
  private final Clock clock;

  public MeetingJoinObservabilityPublisher(
      ApplicationEventPublisher eventPublisher,
      MeetingService meetingService,
      Clock clock) {
    this.eventPublisher = eventPublisher;
    this.meetingService = meetingService;
    this.clock = clock;
  }

  public void publishSuccess(
      String meetingId,
      String roomId,
      String subjectId,
      String role,
      String traceId,
      long durationMs) {
    eventPublisher.publishEvent(new MeetingJoinObservedEvent(
        "MEETING_JOIN_SUCCEEDED",
        "success",
        meetingId,
        roomId,
        subjectId,
        role,
        null,
        null,
        traceId,
        durationMs,
        clock.instant()));
  }

  public void publishFailure(
      String meetingId,
      String subjectId,
      String traceId,
      long durationMs,
      String errorCode) {
    String roomId = resolveRoomId(meetingId);
    eventPublisher.publishEvent(new MeetingJoinObservedEvent(
        "MEETING_JOIN_FAILED",
        "fail",
        meetingId,
        roomId,
        subjectId,
        null,
        errorCode,
        classifyReasonCategory(errorCode),
        traceId,
        durationMs,
        clock.instant()));
  }

  private String resolveRoomId(String meetingId) {
    try {
      Meeting meeting = meetingService.getMeeting(meetingId);
      if (meeting == null || meeting.roomId() == null || meeting.roomId().isBlank()) {
        return null;
      }
      return meeting.roomId();
    } catch (RuntimeException ignored) {
      return null;
    }
  }

  private String classifyReasonCategory(String errorCode) {
    if (ErrorCode.ROLE_MISMATCH.code().equals(errorCode)
        || ErrorCode.ROLE_CONFLICT.code().equals(errorCode)
        || ErrorCode.MEETING_ROLE_CONFLICT.code().equals(errorCode)) {
      return "ROLE";
    }
    if (ErrorCode.CONFIG_INCOMPATIBLE.code().equals(errorCode)) {
      return "CONFIG";
    }
    if (ErrorCode.TOKEN_INVALID.code().equals(errorCode)
        || ErrorCode.TOKEN_REVOKED.code().equals(errorCode)
        || ErrorCode.AUTH_REQUIRED.code().equals(errorCode)) {
      return "TOKEN";
    }
    if (ErrorCode.ACCESS_DENIED.code().equals(errorCode)) {
      return "SSO";
    }
    return "UNKNOWN";
  }
}