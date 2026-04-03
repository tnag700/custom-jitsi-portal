package com.acme.jitsi.domains.admin.api;

import com.acme.jitsi.domains.admin.service.AdminDashboardInvalidRequestException;
import com.acme.jitsi.security.ProblemResponseFacade;
import com.acme.jitsi.shared.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.acme.jitsi.domains.admin.api")
@Order(Ordered.HIGHEST_PRECEDENCE + 22)
class AdminDashboardExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(AdminDashboardExceptionHandler.class);

  private final ProblemResponseFacade problemResponseFacade;

  AdminDashboardExceptionHandler(ProblemResponseFacade problemResponseFacade) {
    this.problemResponseFacade = problemResponseFacade;
  }

  @ExceptionHandler(AdminDashboardInvalidRequestException.class)
  ProblemDetail handleInvalidRequest(AdminDashboardInvalidRequestException ex, HttpServletRequest request) {
    String traceId = problemResponseFacade.resolveTraceId(request);
    if (log.isWarnEnabled()) {
      log.warn(
          "admin_dashboard_invalid_request path={} traceId={} message={}",
          request.getRequestURI(),
          traceId,
          ex.getMessage());
    }
    return problemResponseFacade.buildProblemDetail(
        request,
        HttpStatus.BAD_REQUEST,
        "Некорректный запрос operational dashboard",
        ex.getMessage(),
        ErrorCode.INVALID_REQUEST.code());
  }
}