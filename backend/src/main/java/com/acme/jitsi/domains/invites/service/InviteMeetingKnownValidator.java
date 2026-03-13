package com.acme.jitsi.domains.invites.service;

import com.acme.jitsi.shared.ErrorCode;
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
      throw new InviteExchangeException(HttpStatus.NOT_FOUND, ErrorCode.MEETING_NOT_FOUND.code(), "Встреча не найдена.");
    }
  }
}