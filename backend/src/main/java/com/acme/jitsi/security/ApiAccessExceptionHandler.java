package com.acme.jitsi.security;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ApiAccessExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(ApiAccessExceptionHandler.class);

  private final ProblemResponseFacade problemResponseFacade;

  public ApiAccessExceptionHandler(ProblemResponseFacade problemResponseFacade) {
    this.problemResponseFacade = problemResponseFacade;
  }

  @ExceptionHandler(AccessDeniedException.class)
  public ProblemDetail handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
    String traceId = problemResponseFacade.resolveTraceId(request);
    String errorCode = resolveAccessDeniedCode(ex);
    if (log.isWarnEnabled()) {
      log.warn(
        "access_denied path={} traceId={} code={} message={}",
        request.getRequestURI(),
        traceId,
        errorCode,
        ex.getMessage());
    }

    return problemResponseFacade.buildProblemDetail(
        request,
        HttpStatus.FORBIDDEN,
        "Доступ запрещён",
        ex.getMessage(),
        errorCode);
  }

  private String resolveAccessDeniedCode(AccessDeniedException ex) {
    if (ex == null || ex.getMessage() == null) {
      return "ACCESS_DENIED";
    }
    if ("Tenant claim is required".equals(ex.getMessage())) {
      return "TENANT_CLAIM_REQUIRED";
    }
    if ("Requested tenant is not accessible for current principal".equals(ex.getMessage())) {
      return "TENANT_ACCESS_DENIED";
    }
    return "ACCESS_DENIED";
  }
}