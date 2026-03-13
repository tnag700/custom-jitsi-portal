package com.acme.jitsi.domains.invites.api;

import com.acme.jitsi.domains.invites.service.InviteExchangeException;
import com.acme.jitsi.security.ProblemDetailsMappingPolicy;
import com.acme.jitsi.security.ProblemResponseFacade;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.acme.jitsi.domains.invites.api")
@Order(Ordered.HIGHEST_PRECEDENCE + 24)
class InvitesDomainExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(InvitesDomainExceptionHandler.class);
  private final ProblemResponseFacade problemResponseFacade;

  InvitesDomainExceptionHandler(
      ProblemDetailsMappingPolicy problemDetailsMappingPolicy,
      ProblemResponseFacade problemResponseFacade) {
    this.problemResponseFacade = problemResponseFacade;
  }

  @ExceptionHandler(InviteExchangeException.class)
  ProblemDetail handleInviteExchangeException(InviteExchangeException ex, HttpServletRequest request) {
    return buildAndLogProblemDetail(
        request,
        ex.status(),
        ex.errorCode(),
        ex.getMessage(),
        ex.errorCode(),
        ex);
  }

  private ProblemDetail buildAndLogProblemDetail(
      HttpServletRequest request,
      HttpStatus status,
      String title,
      String message,
      String errorCode,
      Exception ex) {
    String traceId = problemResponseFacade.resolveTraceId(request);
    if (log.isWarnEnabled()) {
      log.warn(
          "invites_domain_failed status={} code={} path={} traceId={} exceptionType={}",
          status.value(),
          errorCode,
          request.getRequestURI(),
          traceId,
          ex.getClass().getSimpleName());
    }
    return problemResponseFacade.buildProblemDetail(request, status, title, message, errorCode);
  }
}