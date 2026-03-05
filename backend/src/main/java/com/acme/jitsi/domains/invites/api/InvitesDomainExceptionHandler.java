package com.acme.jitsi.domains.invites.api;

import com.acme.jitsi.domains.meetings.service.MeetingTokenException;
import com.acme.jitsi.security.ProblemDetailsMappingPolicy;
import com.acme.jitsi.security.ProblemResponseFacade;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.acme.jitsi.domains.invites.api")
@Order(Ordered.HIGHEST_PRECEDENCE + 24)
class InvitesDomainExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(InvitesDomainExceptionHandler.class);
  private final ProblemDetailsMappingPolicy problemDetailsMappingPolicy;
  private final ProblemResponseFacade problemResponseFacade;

  InvitesDomainExceptionHandler(
      ProblemDetailsMappingPolicy problemDetailsMappingPolicy,
      ProblemResponseFacade problemResponseFacade) {
    this.problemDetailsMappingPolicy = problemDetailsMappingPolicy;
    this.problemResponseFacade = problemResponseFacade;
  }

  @ExceptionHandler(MeetingTokenException.class)
  ProblemDetail handleMeetingTokenException(MeetingTokenException ex, HttpServletRequest request) {
    ProblemDetailsMappingPolicy.ProblemDefinition definition =
        problemDetailsMappingPolicy.mapMeetingTokenException(ex);
    String traceId = problemResponseFacade.resolveTraceId(request);
    if (log.isWarnEnabled()) {
      log.warn(
        "invites_domain_failed status={} code={} path={} traceId={} exceptionType={}",
          definition.status().value(),
          definition.errorCode(),
          request.getRequestURI(),
          traceId,
          ex.getClass().getSimpleName());
    }

    return problemResponseFacade.buildProblemDetail(
      request,
      definition.status(),
      definition.title(),
      definition.detail(),
      definition.errorCode());
  }

  @ExceptionHandler(com.acme.jitsi.domains.meetings.service.InviteExhaustedException.class)
  ProblemDetail handleInviteExhaustedException(com.acme.jitsi.domains.meetings.service.InviteExhaustedException ex, HttpServletRequest request) {
    String traceId = problemResponseFacade.resolveTraceId(request);
    if (log.isWarnEnabled()) {
      log.warn(
        "invites_domain_failed status={} code={} path={} traceId={} exceptionType={}",
          409,
          "INVITE_EXHAUSTED",
          request.getRequestURI(),
          traceId,
          ex.getClass().getSimpleName());
    }

    return problemResponseFacade.buildProblemDetail(
      request,
      org.springframework.http.HttpStatus.CONFLICT,
      "Invite Exhausted",
      "The invite link has reached its maximum usage limit.",
      "INVITE_EXHAUSTED");
  }
}