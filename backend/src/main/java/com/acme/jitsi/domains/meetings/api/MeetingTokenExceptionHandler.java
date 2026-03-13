package com.acme.jitsi.domains.meetings.api;

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

@RestControllerAdvice(basePackages = "com.acme.jitsi.domains.meetings.api")
@Order(Ordered.HIGHEST_PRECEDENCE + 22)
class MeetingTokenExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(MeetingTokenExceptionHandler.class);
  private final ProblemDetailsMappingPolicy problemDetailsMappingPolicy;
  private final ProblemResponseFacade problemResponseFacade;

  MeetingTokenExceptionHandler(
      ProblemDetailsMappingPolicy problemDetailsMappingPolicy,
      ProblemResponseFacade problemResponseFacade) {
    this.problemDetailsMappingPolicy = problemDetailsMappingPolicy;
    this.problemResponseFacade = problemResponseFacade;
  }

  @ExceptionHandler(MeetingTokenException.class)
  ProblemDetail handleMeetingTokenException(MeetingTokenException ex, HttpServletRequest request) {
    ProblemDetailsMappingPolicy.ProblemDefinition definition =
        problemDetailsMappingPolicy.mapTokenException(ex.status(), ex.errorCode(), ex.getMessage());
    String traceId = resolveTraceId(request);
    if (log.isWarnEnabled()) {
      log.warn(
        "join_failed status={} code={} path={} traceId={} exceptionType={}",
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

  private String resolveTraceId(HttpServletRequest request) {
    return problemResponseFacade.resolveTraceId(request);
  }
}
