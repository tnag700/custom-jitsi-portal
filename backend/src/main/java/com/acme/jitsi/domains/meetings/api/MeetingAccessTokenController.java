package com.acme.jitsi.domains.meetings.api;

import com.acme.jitsi.domains.meetings.service.MeetingJoinObservabilityPublisher;
import com.acme.jitsi.domains.meetings.service.MeetingTokenException;
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
  private final MeetingJoinObservabilityPublisher meetingJoinObservabilityPublisher;
  private final ProblemResponseFacade problemResponseFacade;

  MeetingAccessTokenController(
      MeetingTokenIssuer meetingAccessTokenService,
      MeetingJoinObservabilityPublisher meetingJoinObservabilityPublisher,
      ProblemResponseFacade problemResponseFacade) {
    this.meetingAccessTokenService = meetingAccessTokenService;
    this.meetingJoinObservabilityPublisher = meetingJoinObservabilityPublisher;
    this.problemResponseFacade = problemResponseFacade;
  }

  @PostMapping("/{meetingId}/access-token")
  MeetingAccessTokenResponse issueAccessToken(
      @PathVariable("meetingId") String meetingId,
      @AuthenticationPrincipal OAuth2User principal,
      HttpServletRequest request) {
    String traceId = problemResponseFacade.resolveTraceId(request);
    String requestId = problemResponseFacade.resolveRequestId(request);
    String subject = principal.getName();
    long startedAt = System.nanoTime();
    if (log.isInfoEnabled()) {
      log.info(
        "join_clicked meetingId={} subject={} path={} traceId={} requestId={}",
        meetingId,
        subject,
        request.getRequestURI(),
        traceId,
        requestId);
    }

    try {
      MeetingTokenIssuer.TokenResult token = meetingAccessTokenService.issueToken(meetingId, principal.getName());
      long durationMs = (System.nanoTime() - startedAt) / 1_000_000L;
      meetingJoinObservabilityPublisher.publishSuccess(
          meetingId,
          token.roomId(),
          subject,
          token.role(),
          traceId,
          durationMs);
      if (log.isInfoEnabled()) {
        log.info(
        "meeting_join_event eventType=MEETING_JOIN_SUCCEEDED result=success meetingId={} roomId={} subjectId={} role={} durationMs={} traceId={} requestId={}",
            meetingId,
            token.roomId(),
            subject,
            token.role(),
            durationMs,
        traceId,
        requestId);
      }

      return new MeetingAccessTokenResponse(token.joinUrl(), token.expiresAt(), token.role());
    } catch (MeetingTokenException ex) {
      long durationMs = (System.nanoTime() - startedAt) / 1_000_000L;
      String reasonCategory = meetingJoinObservabilityPublisher.publishFailure(
          meetingId,
          subject,
          traceId,
          durationMs,
          ex.errorCode());
      if (log.isWarnEnabled()) {
        log.warn(
        "meeting_join_event eventType=MEETING_JOIN_FAILED result=fail meetingId={} subjectId={} errorCode={} reasonCategory={} durationMs={} traceId={} requestId={}",
            meetingId,
            subject,
            ex.errorCode(),
            reasonCategory,
            durationMs,
        traceId,
        requestId);
      }
      throw ex;
    }
  }

}
