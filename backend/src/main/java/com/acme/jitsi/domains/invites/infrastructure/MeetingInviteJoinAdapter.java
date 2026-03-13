package com.acme.jitsi.domains.invites.infrastructure;

import com.acme.jitsi.domains.invites.service.InviteJoinPort;
import com.acme.jitsi.domains.meetings.service.MeetingTokenIssuer;
import org.springframework.stereotype.Component;

@Component
public class MeetingInviteJoinAdapter implements InviteJoinPort {

  private final MeetingTokenIssuer meetingTokenIssuer;

  public MeetingInviteJoinAdapter(MeetingTokenIssuer meetingTokenIssuer) {
    this.meetingTokenIssuer = meetingTokenIssuer;
  }

  @Override
  public JoinResult issueGuestJoin(String meetingId, String guestSubject) {
    MeetingTokenIssuer.TokenResult tokenResult = meetingTokenIssuer.issueGuestToken(meetingId, guestSubject);
    return new JoinResult(tokenResult.joinUrl(), tokenResult.expiresAt(), tokenResult.role());
  }
}