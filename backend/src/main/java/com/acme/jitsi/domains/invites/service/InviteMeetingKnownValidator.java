package com.acme.jitsi.domains.invites.service;

import com.acme.jitsi.domains.meetings.service.MeetingTokenException;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
@Order(50)
class InviteMeetingKnownValidator implements InviteValidator {

  @Override
  public void validate(InviteValidationContext context) {
    String meetingId = context.invite().meetingId();
    if (!context.properties().knownMeetingIds().isEmpty() && !context.properties().knownMeetingIds().contains(meetingId)) {
      throw new MeetingTokenException(HttpStatus.NOT_FOUND, "MEETING_NOT_FOUND", "Встреча не найдена.");
    }
  }
}