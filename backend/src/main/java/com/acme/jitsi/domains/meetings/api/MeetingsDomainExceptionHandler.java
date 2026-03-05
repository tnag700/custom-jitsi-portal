package com.acme.jitsi.domains.meetings.api;

import com.acme.jitsi.domains.meetings.service.InviteAlreadyRevokedException;
import com.acme.jitsi.domains.meetings.service.InviteExpiredException;
import com.acme.jitsi.domains.meetings.service.InviteExhaustedException;
import com.acme.jitsi.domains.meetings.service.InviteNotFoundException;
import com.acme.jitsi.domains.meetings.service.InviteRevokedException;
import com.acme.jitsi.domains.meetings.service.BulkAssignmentValidationException;
import com.acme.jitsi.domains.meetings.service.BulkInviteValidationException;
import com.acme.jitsi.domains.meetings.service.InvalidMeetingDataException;
import com.acme.jitsi.domains.meetings.service.InvalidRecipientFormatException;
import com.acme.jitsi.domains.meetings.service.InvalidMeetingScheduleException;
import com.acme.jitsi.domains.meetings.service.MeetingAssignmentNotFoundException;
import com.acme.jitsi.domains.meetings.service.MeetingFinalizedException;
import com.acme.jitsi.domains.meetings.service.MeetingInvalidRoleException;
import com.acme.jitsi.domains.meetings.service.MeetingNotFoundException;
import com.acme.jitsi.domains.meetings.service.MeetingRoleConflictException;
import com.acme.jitsi.domains.meetings.service.MeetingRoomInactiveException;
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

@RestControllerAdvice(basePackages = "com.acme.jitsi.domains.meetings.api")
@Order(Ordered.HIGHEST_PRECEDENCE + 21)
class MeetingsDomainExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(MeetingsDomainExceptionHandler.class);

  private final ProblemResponseFacade problemResponseFacade;

  MeetingsDomainExceptionHandler(ProblemResponseFacade problemResponseFacade) {
    this.problemResponseFacade = problemResponseFacade;
  }

  @ExceptionHandler(MeetingNotFoundException.class)
  ProblemDetail handleMeetingNotFound(MeetingNotFoundException ex, HttpServletRequest request) {
    return buildAndLogProblemDetail(
        request, HttpStatus.NOT_FOUND, "Встреча не найдена", ex.getMessage(), "MEETING_NOT_FOUND", ex);
  }

  @ExceptionHandler(InvalidMeetingScheduleException.class)
  ProblemDetail handleInvalidSchedule(InvalidMeetingScheduleException ex, HttpServletRequest request) {
    return buildAndLogProblemDetail(
        request, HttpStatus.BAD_REQUEST, "Некорректное расписание", ex.getMessage(), "INVALID_SCHEDULE", ex);
  }

  @ExceptionHandler(InvalidMeetingDataException.class)
  ProblemDetail handleInvalidMeetingData(InvalidMeetingDataException ex, HttpServletRequest request) {
    return buildAndLogProblemDetail(
        request,
        HttpStatus.BAD_REQUEST,
        "Некорректные данные встречи",
        ex.getMessage(),
        "VALIDATION_ERROR",
        ex);
  }

  @ExceptionHandler(MeetingFinalizedException.class)
  ProblemDetail handleMeetingFinalized(MeetingFinalizedException ex, HttpServletRequest request) {
    return buildAndLogProblemDetail(
        request, HttpStatus.CONFLICT, "Встреча финализирована", ex.getMessage(), "MEETING_FINALIZED", ex);
  }

  @ExceptionHandler(MeetingRoomInactiveException.class)
  ProblemDetail handleInactiveRoom(MeetingRoomInactiveException ex, HttpServletRequest request) {
    return buildAndLogProblemDetail(
        request, HttpStatus.CONFLICT, "Комната недоступна", ex.getMessage(), "ROOM_INACTIVE", ex);
  }

  @ExceptionHandler(MeetingAssignmentNotFoundException.class)
  ProblemDetail handleAssignmentNotFound(MeetingAssignmentNotFoundException ex, HttpServletRequest request) {
    return buildAndLogProblemDetail(
        request,
        HttpStatus.NOT_FOUND,
        "Назначение не найдено",
        ex.getMessage(),
        "ASSIGNMENT_NOT_FOUND",
        ex);
  }

  @ExceptionHandler(MeetingInvalidRoleException.class)
  ProblemDetail handleInvalidRole(MeetingInvalidRoleException ex, HttpServletRequest request) {
    return buildAndLogProblemDetail(
        request, HttpStatus.BAD_REQUEST, "Недопустимая роль", ex.getMessage(), ex.errorCode(), ex);
  }

  @ExceptionHandler(MeetingRoleConflictException.class)
  ProblemDetail handleRoleConflict(MeetingRoleConflictException ex, HttpServletRequest request) {
    return buildAndLogProblemDetail(
        request, HttpStatus.CONFLICT, "Конфликт ролей", ex.getMessage(), ex.errorCode(), ex);
  }

  @ExceptionHandler(InviteNotFoundException.class)
  ProblemDetail handleInviteNotFound(InviteNotFoundException ex, HttpServletRequest request) {
    return buildAndLogProblemDetail(
        request, HttpStatus.NOT_FOUND, "Инвайт не найден", ex.getMessage(), "INVITE_NOT_FOUND", ex);
  }

  @ExceptionHandler(InviteAlreadyRevokedException.class)
  ProblemDetail handleInviteAlreadyRevoked(InviteAlreadyRevokedException ex, HttpServletRequest request) {
    return buildAndLogProblemDetail(
        request, HttpStatus.CONFLICT, "Инвайт уже отозван", ex.getMessage(), "INVITE_ALREADY_REVOKED", ex);
  }

  @ExceptionHandler(InviteExpiredException.class)
  ProblemDetail handleInviteExpired(InviteExpiredException ex, HttpServletRequest request) {
    return buildAndLogProblemDetail(
        request, HttpStatus.GONE, "Срок действия инвайта истёк", ex.getMessage(), "INVITE_EXPIRED", ex);
  }

  @ExceptionHandler(InviteExhaustedException.class)
  ProblemDetail handleInviteExhausted(InviteExhaustedException ex, HttpServletRequest request) {
    return buildAndLogProblemDetail(
        request, HttpStatus.CONFLICT, "Лимит использований исчерпан", ex.getMessage(), "INVITE_EXHAUSTED", ex);
  }

  @ExceptionHandler(InviteRevokedException.class)
  ProblemDetail handleInviteRevoked(InviteRevokedException ex, HttpServletRequest request) {
    return buildAndLogProblemDetail(
        request, HttpStatus.GONE, "Инвайт отозван", ex.getMessage(), "INVITE_REVOKED", ex);
  }

  @ExceptionHandler(BulkInviteValidationException.class)
  ProblemDetail handleBulkInviteValidation(BulkInviteValidationException ex, HttpServletRequest request) {
    ProblemDetail problem = buildAndLogProblemDetail(
        request,
        HttpStatus.UNPROCESSABLE_ENTITY,
        "Частичная ошибка массового создания инвайтов",
        ex.getMessage(),
        "BULK_INVITE_VALIDATION_FAILED",
        ex);
    problem.setProperty("errors", ex.errors());
    if (ex.result() != null) {
      problem.setProperty("created", ex.result().created());
      problem.setProperty("skipped", ex.result().skipped());
      problem.setProperty("summary", ex.result().summary());
    }
    return problem;
  }

  @ExceptionHandler(BulkAssignmentValidationException.class)
  ProblemDetail handleBulkAssignmentValidation(BulkAssignmentValidationException ex, HttpServletRequest request) {
    HttpStatus status = "MEETING_ROLE_CONFLICT".equals(ex.errorCode()) ? HttpStatus.CONFLICT : HttpStatus.BAD_REQUEST;
    ProblemDetail problem = buildAndLogProblemDetail(
        request,
        status,
        "Ошибка массового назначения",
        ex.getMessage(),
        ex.errorCode(),
        ex);
    problem.setProperty("rowIndex", ex.rowIndex());
    problem.setProperty("subjectId", ex.subjectId());
    return problem;
  }

  @ExceptionHandler(InvalidRecipientFormatException.class)
  ProblemDetail handleInvalidRecipient(InvalidRecipientFormatException ex, HttpServletRequest request) {
    ProblemDetail problem = buildAndLogProblemDetail(
        request,
        HttpStatus.BAD_REQUEST,
        "Некорректный получатель",
        ex.getMessage(),
        "INVALID_RECIPIENT_FORMAT",
        ex);
    problem.setProperty("rowIndex", ex.rowIndex());
    problem.setProperty("recipient", ex.recipient());
    return problem;
  }

  @ExceptionHandler(IllegalArgumentException.class)
  ProblemDetail handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
    String path = request.getRequestURI();
    String errorCode = isInviteValidationError(ex, path) ? "VALIDATION_ERROR" : "INVALID_REQUEST";
    return buildAndLogProblemDetail(
        request, HttpStatus.BAD_REQUEST, "Некорректный запрос", ex.getMessage(), errorCode, ex);
  }

  private boolean isInviteValidationError(IllegalArgumentException ex, String path) {
    if (path != null && path.contains("/invites/bulk")) {
      return true;
    }

    String message = ex.getMessage();
    if (message == null) {
      return false;
    }

    return message.contains("maxUses")
        || message.contains("expiresInHours")
        || message.contains("defaultMaxUses")
        || message.contains("defaultTtlMinutes")
        || message.contains("defaultRole")
        || message.contains("HOST role");
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
        "meetings_domain_failed status={} code={} path={} traceId={} exceptionType={} message={}",
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