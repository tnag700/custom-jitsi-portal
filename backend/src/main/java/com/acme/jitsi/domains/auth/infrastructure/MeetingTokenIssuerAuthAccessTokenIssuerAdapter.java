package com.acme.jitsi.domains.auth.infrastructure;

import com.acme.jitsi.domains.auth.service.AuthAccessTokenIssuer;
import com.acme.jitsi.domains.auth.service.AuthTokenException;
import com.acme.jitsi.domains.meetings.service.MeetingTokenException;
import com.acme.jitsi.domains.meetings.service.MeetingTokenIssuer;
import org.springframework.stereotype.Component;

@Component
public class MeetingTokenIssuerAuthAccessTokenIssuerAdapter implements AuthAccessTokenIssuer {

  private final MeetingTokenIssuer meetingTokenIssuer;

  public MeetingTokenIssuerAuthAccessTokenIssuerAdapter(MeetingTokenIssuer meetingTokenIssuer) {
    this.meetingTokenIssuer = meetingTokenIssuer;
  }

  @Override
  public AccessTokenResult issueAccessToken(String meetingId, String subject) {
    try {
      MeetingTokenIssuer.AccessTokenResult result = meetingTokenIssuer.issueAccessToken(meetingId, subject);
      return new AccessTokenResult(result.accessToken(), result.expiresAt(), result.role());
    } catch (MeetingTokenException ex) {
      throw new AuthTokenException(ex.status(), ex.errorCode(), ex.getMessage(), ex);
    }
  }
}