package com.acme.jitsi.domains.rooms.api;

import com.acme.jitsi.domains.rooms.service.ConfigSetInvalidException;
import com.acme.jitsi.domains.rooms.service.InvalidRoomDataException;
import com.acme.jitsi.domains.rooms.service.RoomAlreadyClosedException;
import com.acme.jitsi.domains.rooms.service.RoomHasActiveMeetingsException;
import com.acme.jitsi.domains.rooms.service.RoomNameConflictException;
import com.acme.jitsi.domains.rooms.service.RoomNotFoundException;
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

@RestControllerAdvice(basePackages = "com.acme.jitsi.domains.rooms.api")
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
class RoomsDomainExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(RoomsDomainExceptionHandler.class);

  private final ProblemResponseFacade problemResponseFacade;

  RoomsDomainExceptionHandler(ProblemResponseFacade problemResponseFacade) {
    this.problemResponseFacade = problemResponseFacade;
  }

  @ExceptionHandler(RoomNotFoundException.class)
  ProblemDetail handleRoomNotFound(RoomNotFoundException ex, HttpServletRequest request) {
    return buildAndLogProblemDetail(
      request, HttpStatus.NOT_FOUND, "Комната не найдена", ex.getMessage(), ErrorCode.ROOM_NOT_FOUND.code(), ex);
  }

  @ExceptionHandler(RoomNameConflictException.class)
  ProblemDetail handleRoomNameConflict(RoomNameConflictException ex, HttpServletRequest request) {
    return buildAndLogProblemDetail(
      request, HttpStatus.CONFLICT, "Имя комнаты занято", ex.getMessage(), ErrorCode.ROOM_NAME_CONFLICT.code(), ex);
  }

  @ExceptionHandler(ConfigSetInvalidException.class)
  ProblemDetail handleConfigSetInvalid(ConfigSetInvalidException ex, HttpServletRequest request) {
    return buildAndLogProblemDetail(
      request, HttpStatus.BAD_REQUEST, "Неверная конфигурация", ex.getMessage(), ErrorCode.CONFIG_SET_INVALID.code(), ex);
  }

  @ExceptionHandler(InvalidRoomDataException.class)
  ProblemDetail handleInvalidRoomData(InvalidRoomDataException ex, HttpServletRequest request) {
    return buildAndLogProblemDetail(
      request, HttpStatus.BAD_REQUEST, "Некорректные данные", ex.getMessage(), ErrorCode.VALIDATION_ERROR.code(), ex);
  }

  @ExceptionHandler(RoomHasActiveMeetingsException.class)
  ProblemDetail handleRoomHasActiveMeetings(RoomHasActiveMeetingsException ex, HttpServletRequest request) {
    return buildAndLogProblemDetail(
        request,
        HttpStatus.CONFLICT,
        "Комната имеет активные встречи",
        ex.getMessage(),
        ErrorCode.ROOM_HAS_ACTIVE_MEETINGS.code(),
        ex);
  }

  @ExceptionHandler(RoomAlreadyClosedException.class)
  ProblemDetail handleRoomAlreadyClosed(RoomAlreadyClosedException ex, HttpServletRequest request) {
    return buildAndLogProblemDetail(
      request, HttpStatus.BAD_REQUEST, "Комната уже закрыта", ex.getMessage(), ErrorCode.ROOM_ALREADY_CLOSED.code(), ex);
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
        "rooms_domain_failed status={} code={} path={} traceId={} exceptionType={} message={}",
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