package com.acme.jitsi.domains.invites.infrastructure;

import com.acme.jitsi.domains.invites.service.InviteExchangeException;
import com.acme.jitsi.domains.invites.service.InviteMeetingStatePort;
import com.acme.jitsi.domains.meetings.service.MeetingStateGuard;
import com.acme.jitsi.domains.meetings.service.MeetingTokenException;
import org.springframework.stereotype.Component;

@Component
public class MeetingStateGuardInviteMeetingStatePort implements InviteMeetingStatePort {

  private final MeetingStateGuard meetingStateGuard;

  public MeetingStateGuardInviteMeetingStatePort(MeetingStateGuard meetingStateGuard) {
    this.meetingStateGuard = meetingStateGuard;
  }

  @Override
  public void assertJoinAllowed(String meetingId) {
    try {
      meetingStateGuard.assertJoinAllowed(meetingId);
    } catch (MeetingTokenException ex) {
      throw new InviteExchangeException(ex.status(), ex.errorCode(), ex.getMessage(), ex);
    }
  }
}