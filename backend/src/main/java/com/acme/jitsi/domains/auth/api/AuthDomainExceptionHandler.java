package com.acme.jitsi.domains.auth.api;

import com.acme.jitsi.domains.auth.service.AuthTokenException;
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

@RestControllerAdvice(basePackages = "com.acme.jitsi.domains.auth.api")
@Order(Ordered.HIGHEST_PRECEDENCE + 23)
class AuthDomainExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(AuthDomainExceptionHandler.class);
  private final ProblemDetailsMappingPolicy problemDetailsMappingPolicy;
  private final ProblemResponseFacade problemResponseFacade;

  AuthDomainExceptionHandler(
      ProblemDetailsMappingPolicy problemDetailsMappingPolicy,
      ProblemResponseFacade problemResponseFacade) {
    this.problemDetailsMappingPolicy = problemDetailsMappingPolicy;
    this.problemResponseFacade = problemResponseFacade;
  }

  @ExceptionHandler(AuthTokenException.class)
  ProblemDetail handleAuthTokenException(AuthTokenException ex, HttpServletRequest request) {
    ProblemDetailsMappingPolicy.ProblemDefinition definition =
        problemDetailsMappingPolicy.mapTokenException(ex.status(), ex.errorCode(), ex.getMessage());
    String traceId = problemResponseFacade.resolveTraceId(request);
    if (log.isWarnEnabled()) {
      log.warn(
        "auth_domain_failed status={} code={} path={} traceId={} exceptionType={}",
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
}