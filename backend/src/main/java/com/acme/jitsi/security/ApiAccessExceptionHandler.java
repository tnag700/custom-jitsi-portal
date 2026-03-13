package com.acme.jitsi.security;

import com.acme.jitsi.shared.ErrorCode;
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
    String detail = ex.getMessage() != null ? ex.getMessage() : "Доступ запрещён";
    if (log.isWarnEnabled()) {
      log.warn(
        "access_denied path={} traceId={} code={} message={}",
        request.getRequestURI(),
        traceId,
        errorCode,
        detail);
    }

    return problemResponseFacade.buildProblemDetail(
        request,
        HttpStatus.FORBIDDEN,
        "Доступ запрещён",
        detail,
        errorCode);
  }

  private String resolveAccessDeniedCode(AccessDeniedException ex) {
    if (ex.getMessage() == null) {
      return ErrorCode.ACCESS_DENIED.code();
    }
    if ("Tenant claim is required".equals(ex.getMessage())) {
      return ErrorCode.TENANT_CLAIM_REQUIRED.code();
    }
    if ("Requested tenant is not accessible for current principal".equals(ex.getMessage())) {
      return ErrorCode.TENANT_ACCESS_DENIED.code();
    }
    return ErrorCode.ACCESS_DENIED.code();
  }
}