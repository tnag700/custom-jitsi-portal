package com.acme.jitsi.domains.invites.service;

import com.acme.jitsi.domains.meetings.service.MeetingStateGuard;
import com.acme.jitsi.domains.meetings.service.MeetingTokenException;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
@Order(60)
class InviteMeetingStateValidator implements InviteValidator {

  private final MeetingStateGuard meetingStateGuard;

  InviteMeetingStateValidator(MeetingStateGuard meetingStateGuard) {
    this.meetingStateGuard = meetingStateGuard;
  }

  @Override
  public void validate(InviteValidationContext context) {
    String meetingId = context.invite().meetingId();
    if (context.properties().canceledMeetingIds().contains(meetingId)) {
      throw new MeetingTokenException(HttpStatus.CONFLICT, "MEETING_CANCELED", "Встреча отменена.");
    }
    if (context.properties().closedMeetingIds().contains(meetingId)) {
      throw new MeetingTokenException(HttpStatus.CONFLICT, "MEETING_ENDED", "Встреча завершена.");
    }

    if (!context.properties().knownMeetingIds().isEmpty() && context.properties().knownMeetingIds().contains(meetingId)) {
      return;
    }

    meetingStateGuard.assertJoinAllowed(meetingId);
  }
}