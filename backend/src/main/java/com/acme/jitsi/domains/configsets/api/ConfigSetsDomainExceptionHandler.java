package com.acme.jitsi.domains.configsets.api;

import com.acme.jitsi.domains.configsets.service.ConfigSetActivationNotAllowedException;
import com.acme.jitsi.domains.configsets.service.ConfigSetInvalidDataException;
import com.acme.jitsi.domains.configsets.service.ConfigSetNameConflictException;
import com.acme.jitsi.domains.configsets.service.ConfigSetNotFoundException;
import com.acme.jitsi.domains.configsets.service.ConfigSetRollbackNotAllowedException;
import com.acme.jitsi.domains.configsets.service.ConfigSetRolloutNotAllowedException;
import com.acme.jitsi.domains.configsets.service.ConfigSetRolloutValidationFailedException;
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

@RestControllerAdvice(basePackages = "com.acme.jitsi.domains.configsets.api")
@Order(Ordered.HIGHEST_PRECEDENCE + 22)
class ConfigSetsDomainExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(ConfigSetsDomainExceptionHandler.class);

  private final ProblemResponseFacade problemResponseFacade;

  ConfigSetsDomainExceptionHandler(ProblemResponseFacade problemResponseFacade) {
    this.problemResponseFacade = problemResponseFacade;
  }

  @ExceptionHandler(ConfigSetInvalidDataException.class)
  ProblemDetail handleInvalid(ConfigSetInvalidDataException ex, HttpServletRequest request) {
    return buildAndLogProblemDetail(
        request,
        HttpStatus.BAD_REQUEST,
        "Некорректные данные конфиг-набора",
        ex.getMessage(),
        "CONFIG_SET_INVALID",
        ex);
  }

  @ExceptionHandler(ConfigSetNotFoundException.class)
  ProblemDetail handleNotFound(ConfigSetNotFoundException ex, HttpServletRequest request) {
    return buildAndLogProblemDetail(
        request,
        HttpStatus.NOT_FOUND,
        "Конфиг-набор не найден",
        ex.getMessage(),
        "CONFIG_SET_NOT_FOUND",
        ex);
  }

  @ExceptionHandler(ConfigSetNameConflictException.class)
  ProblemDetail handleConflict(ConfigSetNameConflictException ex, HttpServletRequest request) {
    return buildAndLogProblemDetail(
        request,
        HttpStatus.CONFLICT,
        "Имя конфиг-набора уже занято",
        ex.getMessage(),
        "CONFIG_SET_NAME_CONFLICT",
        ex);
  }

  @ExceptionHandler(ConfigSetActivationNotAllowedException.class)
  ProblemDetail handleActivationNotAllowed(
      ConfigSetActivationNotAllowedException ex,
      HttpServletRequest request) {
    return buildAndLogProblemDetail(
        request,
        HttpStatus.UNPROCESSABLE_ENTITY,
        "Активация конфиг-набора не разрешена",
        ex.getMessage(),
        "CONFIG_SET_ACTIVATION_NOT_ALLOWED",
        ex);
  }

  @ExceptionHandler(ConfigSetRolloutNotAllowedException.class)
  ProblemDetail handleRolloutNotAllowed(
      ConfigSetRolloutNotAllowedException ex,
      HttpServletRequest request) {
    return buildAndLogProblemDetail(
        request,
        HttpStatus.UNPROCESSABLE_ENTITY,
        "Rollout конфиг-набора не разрешён",
        ex.getMessage(),
        "CONFIG_SET_ROLLOUT_NOT_ALLOWED",
        ex);
  }

  @ExceptionHandler(ConfigSetRolloutValidationFailedException.class)
  ProblemDetail handleRolloutValidationFailed(
      ConfigSetRolloutValidationFailedException ex,
      HttpServletRequest request) {
    return buildAndLogProblemDetail(
        request,
        HttpStatus.UNPROCESSABLE_ENTITY,
        "Конфиг-набор несовместим",
        ex.getMessage(),
        "CONFIG_INCOMPATIBLE",
        ex);
  }

  @ExceptionHandler(ConfigSetRollbackNotAllowedException.class)
  ProblemDetail handleRollbackNotAllowed(
      ConfigSetRollbackNotAllowedException ex,
      HttpServletRequest request) {
    return buildAndLogProblemDetail(
        request,
        HttpStatus.UNPROCESSABLE_ENTITY,
        "Rollback конфиг-набора не разрешён",
        ex.getMessage(),
        "CONFIG_SET_ROLLBACK_NOT_ALLOWED",
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
        "configsets_domain_failed status={} code={} path={} traceId={} exceptionType={} message={}",
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