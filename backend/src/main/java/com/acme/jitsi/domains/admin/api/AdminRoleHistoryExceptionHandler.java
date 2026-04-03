package com.acme.jitsi.domains.admin.api;

import com.acme.jitsi.domains.admin.service.AdminRoleHistoryInvalidRequestException;
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
@Order(Ordered.HIGHEST_PRECEDENCE + 24)
class AdminRoleHistoryExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(AdminRoleHistoryExceptionHandler.class);

  private final ProblemResponseFacade problemResponseFacade;

  AdminRoleHistoryExceptionHandler(ProblemResponseFacade problemResponseFacade) {
    this.problemResponseFacade = problemResponseFacade;
  }

  @ExceptionHandler(AdminRoleHistoryInvalidRequestException.class)
  ProblemDetail handleInvalidRequest(AdminRoleHistoryInvalidRequestException ex, HttpServletRequest request) {
    log.warn("admin_role_history_invalid_request path={} message={}", request.getRequestURI(), ex.getMessage());
    return problemResponseFacade.buildProblemDetail(
        request,
        HttpStatus.BAD_REQUEST,
        "Некорректный запрос role history",
        ex.getMessage(),
        ErrorCode.INVALID_REQUEST.code());
  }
}