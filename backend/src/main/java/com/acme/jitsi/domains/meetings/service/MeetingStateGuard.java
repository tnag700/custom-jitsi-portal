package com.acme.jitsi.domains.meetings.service;

import com.acme.jitsi.shared.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class MeetingStateGuard {

  private final MeetingRepository meetingRepository;
  private final Clock clock;
  private final MeetingTokenProperties tokenProperties;

  public MeetingStateGuard(MeetingRepository meetingRepository, Clock clock, MeetingTokenProperties tokenProperties) {
    this.meetingRepository = meetingRepository;
    this.clock = clock;
    this.tokenProperties = tokenProperties;
  }

  public void assertJoinAllowed(String meetingId) {
    Meeting meeting = meetingRepository.findById(meetingId)
        .orElseGet(() -> {
          if (tokenProperties.knownMeetingIds().contains(meetingId)) {
            return null;
          }
          throw new MeetingTokenException(
              HttpStatus.NOT_FOUND,
              ErrorCode.MEETING_NOT_FOUND.code(),
              "Встреча не найдена.");
        });

    if (meeting == null) {
      return;
    }

    assertJoinAllowed(meeting);
  }

  public void assertJoinAllowed(Meeting meeting) {
    if (meeting.status() == MeetingStatus.CANCELED) {
      throw new MeetingTokenException(HttpStatus.CONFLICT, ErrorCode.MEETING_CANCELED.code(), "Встреча отменена.");
    }

    if (meeting.status() == MeetingStatus.ENDED || Instant.now(clock).isAfter(meeting.endsAt())) {
      throw new MeetingTokenException(HttpStatus.CONFLICT, ErrorCode.MEETING_ENDED.code(), "Встреча завершена.");
    }
  }
}