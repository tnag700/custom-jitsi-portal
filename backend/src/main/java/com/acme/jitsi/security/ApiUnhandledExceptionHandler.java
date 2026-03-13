package com.acme.jitsi.security;

import com.acme.jitsi.shared.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class ApiUnhandledExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(ApiUnhandledExceptionHandler.class);

  private final ProblemResponseFacade problemResponseFacade;

  public ApiUnhandledExceptionHandler(ProblemResponseFacade problemResponseFacade) {
    this.problemResponseFacade = problemResponseFacade;
  }

  @ExceptionHandler(InvalidDataAccessApiUsageException.class)
  public ProblemDetail handleInvalidPersistedState(
      InvalidDataAccessApiUsageException ex,
      HttpServletRequest request) {
    String traceId = problemResponseFacade.resolveTraceId(request);
    String requestUri = request.getRequestURI();
    Throwable root = resolveRootCause(ex);
    String exceptionType = resolveExceptionType(ex);
    String rootCauseType = resolveExceptionType(root);
    String rootCauseMessage = resolveExceptionMessage(root);
    if (log.isErrorEnabled()) {
      log.error(
        "invalid_persisted_state path={} traceId={} exceptionType={} rootCauseType={} rootCauseMessage={}",
        requestUri,
        traceId,
        exceptionType,
        rootCauseType,
        rootCauseMessage);
    }

    return problemResponseFacade.buildProblemDetail(
        request,
        HttpStatus.INTERNAL_SERVER_ERROR,
        "Внутренняя ошибка данных",
        "Сервис обнаружил некорректные сохранённые данные. Обратитесь в поддержку с traceId.",
        ErrorCode.INVALID_PERSISTED_STATE.code());
  }

  @ExceptionHandler(Exception.class)
  public ProblemDetail handleUnhandledException(Exception ex, HttpServletRequest request) {
    String traceId = problemResponseFacade.resolveTraceId(request);
    String requestUri = request.getRequestURI();
    Throwable root = resolveRootCause(ex);
    String exceptionType = resolveExceptionType(ex);
    String rootCauseType = resolveExceptionType(root);
    String rootCauseMessage = resolveExceptionMessage(root);
    if (log.isErrorEnabled()) {
      log.error(
        "unhandled_api_exception path={} traceId={} exceptionType={} rootCauseType={} rootCauseMessage={}",
        requestUri,
        traceId,
        exceptionType,
        rootCauseType,
        rootCauseMessage);
    }

    return problemResponseFacade.buildProblemDetail(
        request,
        HttpStatus.INTERNAL_SERVER_ERROR,
        "Внутренняя ошибка сервиса",
        "Сервис временно недоступен. Повторите попытку позже или обратитесь в поддержку с traceId.",
        ErrorCode.INTERNAL_ERROR.code());
  }

  private Throwable resolveRootCause(Throwable throwable) {
    Throwable current = throwable;
    Throwable cause = current.getCause();
    while (cause != null && cause != current) {
      current = cause;
      cause = current.getCause();
    }
    return current;
  }

  private String resolveExceptionType(Throwable throwable) {
    if (throwable instanceof IllegalArgumentException) {
      return "IllegalArgumentException";
    }
    if (throwable instanceof IllegalStateException) {
      return "IllegalStateException";
    }
    if (throwable instanceof RuntimeException) {
      return "RuntimeException";
    }
    if (throwable instanceof Exception) {
      return "Exception";
    }
    return "Throwable";
  }

  private String resolveExceptionMessage(Throwable throwable) {
    if (throwable instanceof IllegalArgumentException iae) {
      String message = iae.getMessage();
      return message == null ? "" : message;
    }
    if (throwable instanceof IllegalStateException ise) {
      String message = ise.getMessage();
      return message == null ? "" : message;
    }
    String message = throwable.toString();
    return message == null ? "" : message;
  }
}