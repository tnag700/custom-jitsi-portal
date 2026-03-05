package com.acme.jitsi.domains.profiles.api;

import com.acme.jitsi.domains.profiles.service.ProfileNotFoundException;
import com.acme.jitsi.domains.profiles.service.ProfileValidationException;
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

@RestControllerAdvice(basePackages = "com.acme.jitsi.domains.profiles.api")
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
class ProfileDomainExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(ProfileDomainExceptionHandler.class);

  private final ProblemResponseFacade problemResponseFacade;

  ProfileDomainExceptionHandler(ProblemResponseFacade problemResponseFacade) {
    this.problemResponseFacade = problemResponseFacade;
  }

  @ExceptionHandler(ProfileNotFoundException.class)
  ProblemDetail handleProfileNotFound(ProfileNotFoundException ex, HttpServletRequest request) {
    return buildAndLogProblemDetail(
        request, HttpStatus.NOT_FOUND, "Профиль не найден", ex.getMessage(), "PROFILE_NOT_FOUND", ex);
  }

  @ExceptionHandler(ProfileValidationException.class)
  ProblemDetail handleProfileValidation(ProfileValidationException ex, HttpServletRequest request) {
    return buildAndLogProblemDetail(
        request,
        HttpStatus.BAD_REQUEST,
        "Ошибка валидации профиля",
        ex.getMessage(),
        "PROFILE_VALIDATION_FAILED",
        ex);
  }

  private ProblemDetail buildAndLogProblemDetail(
      HttpServletRequest request,
      HttpStatus status,
      String title,
      String detail,
      String errorCode,
      Exception ex) {
    String traceId = problemResponseFacade.resolveTraceId(request);
    if (log.isWarnEnabled()) {
      log.warn(
        "profiles_domain_failed status={} code={} path={} traceId={} exceptionType={} message={}",
        status.value(),
        errorCode,
        request.getRequestURI(),
        traceId,
        ex.getClass().getSimpleName(),
        ex.getMessage());
    }
    return problemResponseFacade.buildProblemDetail(request, status, title, detail, errorCode);
  }
}
