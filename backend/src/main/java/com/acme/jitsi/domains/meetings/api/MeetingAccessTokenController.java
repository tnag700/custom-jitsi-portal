package com.acme.jitsi.domains.meetings.api;

import com.acme.jitsi.domains.meetings.service.MeetingTokenIssuer;
import com.acme.jitsi.security.ProblemResponseFacade;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/meetings", version = "v1")
class MeetingAccessTokenController {

  private static final Logger log = LoggerFactory.getLogger(MeetingAccessTokenController.class);

  private final MeetingTokenIssuer meetingAccessTokenService;
  private final ProblemResponseFacade problemResponseFacade;

  MeetingAccessTokenController(
      MeetingTokenIssuer meetingAccessTokenService,
      ProblemResponseFacade problemResponseFacade) {
    this.meetingAccessTokenService = meetingAccessTokenService;
    this.problemResponseFacade = problemResponseFacade;
  }

  @PostMapping("/{meetingId}/access-token")
  MeetingAccessTokenResponse issueAccessToken(
      @PathVariable("meetingId") String meetingId,
      @AuthenticationPrincipal OAuth2User principal,
      HttpServletRequest request) {
    String traceId = problemResponseFacade.resolveTraceId(request);
    String subject = principal.getName();
    long startedAt = System.nanoTime();
    if (log.isInfoEnabled()) {
      log.info(
        "join_clicked meetingId={} subject={} path={} traceId={}",
        meetingId,
        subject,
        request.getRequestURI(),
        traceId);
    }

    MeetingTokenIssuer.TokenResult token = meetingAccessTokenService.issueToken(meetingId, principal.getName());
    long durationMs = (System.nanoTime() - startedAt) / 1_000_000L;
    if (log.isInfoEnabled()) {
      log.info(
        "join_succeeded meetingId={} subject={} role={} durationMs={} traceId={}",
        meetingId,
        subject,
        token.role(),
        durationMs,
        traceId);
    }

    return new MeetingAccessTokenResponse(token.joinUrl(), token.expiresAt(), token.role());
  }
}
