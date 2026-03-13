package com.acme.jitsi.domains.invites.service;

import com.acme.jitsi.shared.ErrorCode;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
@Order(60)
class InviteMeetingStateValidator implements InviteValidator {

  private final InviteMeetingStatePort inviteMeetingStatePort;

  InviteMeetingStateValidator(InviteMeetingStatePort inviteMeetingStatePort) {
    this.inviteMeetingStatePort = inviteMeetingStatePort;
  }

  @Override
  public void validate(InviteValidationContext context) {
    String meetingId = context.invite().meetingId();
    if (context.properties().canceledMeetingIds().contains(meetingId)) {
      throw new InviteExchangeException(HttpStatus.CONFLICT, ErrorCode.MEETING_CANCELED.code(), "Встреча отменена.");
    }
    if (context.properties().closedMeetingIds().contains(meetingId)) {
      throw new InviteExchangeException(HttpStatus.CONFLICT, ErrorCode.MEETING_ENDED.code(), "Встреча завершена.");
    }

    if (!context.properties().knownMeetingIds().isEmpty() && context.properties().knownMeetingIds().contains(meetingId)) {
      return;
    }

    inviteMeetingStatePort.assertJoinAllowed(meetingId);
  }
}