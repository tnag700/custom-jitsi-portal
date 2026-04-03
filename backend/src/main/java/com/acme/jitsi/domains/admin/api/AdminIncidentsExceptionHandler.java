package com.acme.jitsi.domains.admin.api;

import com.acme.jitsi.domains.admin.service.AdminIncidentNotFoundException;
import com.acme.jitsi.domains.admin.service.AdminIncidentsInvalidRequestException;
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
@Order(Ordered.HIGHEST_PRECEDENCE + 23)
class AdminIncidentsExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(AdminIncidentsExceptionHandler.class);

  private final ProblemResponseFacade problemResponseFacade;

  AdminIncidentsExceptionHandler(ProblemResponseFacade problemResponseFacade) {
    this.problemResponseFacade = problemResponseFacade;
  }

  @ExceptionHandler(AdminIncidentsInvalidRequestException.class)
  ProblemDetail handleInvalidRequest(AdminIncidentsInvalidRequestException ex, HttpServletRequest request) {
    log.warn("admin_incidents_invalid_request path={} message={}", request.getRequestURI(), ex.getMessage());
    return problemResponseFacade.buildProblemDetail(
        request,
        HttpStatus.BAD_REQUEST,
        "Некорректный запрос incident cabinet",
        ex.getMessage(),
        ErrorCode.INVALID_REQUEST.code());
  }

  @ExceptionHandler(AdminIncidentNotFoundException.class)
  ProblemDetail handleNotFound(AdminIncidentNotFoundException ex, HttpServletRequest request) {
    log.warn("admin_incident_not_found path={} message={}", request.getRequestURI(), ex.getMessage());
    return problemResponseFacade.buildProblemDetail(
        request,
        HttpStatus.NOT_FOUND,
        "Инцидент не найден",
        ex.getMessage(),
        ErrorCode.INCIDENT_NOT_FOUND.code());
  }
}