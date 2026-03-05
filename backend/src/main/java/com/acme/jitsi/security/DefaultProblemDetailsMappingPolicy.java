package com.acme.jitsi.security;

import com.acme.jitsi.domains.meetings.service.MeetingTokenException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class DefaultProblemDetailsMappingPolicy implements ProblemDetailsMappingPolicy {

  private static final ProblemDefinition AUTH_REQUIRED_FOR_AUTH_ERROR =
      new ProblemDefinition(
          HttpStatus.UNAUTHORIZED,
          "Требуется вход",
          "Не удалось выполнить вход. Попробуйте снова или обратитесь в поддержку.",
          "AUTH_REQUIRED");

  private static final ProblemDefinition ACCESS_DENIED_FOR_AUTH_ERROR =
      new ProblemDefinition(
          HttpStatus.FORBIDDEN,
          "Вход отклонен",
          "Провайдер входа отклонил запрос или аутентификация не завершена.",
          "ACCESS_DENIED");

  private static final ProblemDefinition SECURITY_AUTH_REQUIRED =
      new ProblemDefinition(
          HttpStatus.UNAUTHORIZED,
          "Требуется вход",
          "Сессия отсутствует или истекла. Выполните вход через SSO.",
          "AUTH_REQUIRED");

  private static final ProblemDefinition SECURITY_ACCESS_DENIED =
      new ProblemDefinition(
          HttpStatus.FORBIDDEN,
          "Доступ запрещен",
          "Недостаточно прав для выполнения операции.",
          "ACCESS_DENIED");

  private static final Map<String, String> MEETING_TITLE_BY_ERROR_CODE =
      Map.of(
          "INVITE_EXHAUSTED", "Инвайт исчерпан",
          "INVITE_EXPIRED", "Инвайт просрочен",
          "INVITE_REVOKED", "Инвайт отозван",
          "ROOM_CLOSED", "Встреча недоступна",
          "MEETING_NOT_FOUND", "Встреча не найдена",
        "MEETING_CANCELED", "Встреча отменена",
        "MEETING_ENDED", "Встреча завершена",
        "MEETING_FINALIZED", "Встреча финализирована",
          "INVALID_INVITE", "Инвайт недействителен");

  @Override
  public ProblemDefinition mapAuthErrorCode(String code) {
    if ("ACCESS_DENIED".equals(code)) {
      return ACCESS_DENIED_FOR_AUTH_ERROR;
    }
    return AUTH_REQUIRED_FOR_AUTH_ERROR;
  }

  @Override
  public ProblemDefinition mapSecurityAuthRequired() {
    return SECURITY_AUTH_REQUIRED;
  }

  @Override
  public ProblemDefinition mapSecurityAccessDenied() {
    return SECURITY_ACCESS_DENIED;
  }

  @Override
  public ProblemDefinition mapMeetingTokenException(MeetingTokenException exception) {
    return new ProblemDefinition(
        exception.status(),
        resolveMeetingTitle(exception),
        exception.getMessage(),
        exception.errorCode());
  }

  @Override
  public String resolveValidationErrorCode(String requestUri) {
    if (requestUri != null && requestUri.startsWith("/api/v1/profile/")) {
      return "PROFILE_VALIDATION_FAILED";
    }
    if (requestUri != null && requestUri.startsWith("/api/v1/invites/")) {
      return "INVALID_INVITE";
    }
    return "INVALID_REQUEST";
  }

  private String resolveMeetingTitle(MeetingTokenException exception) {
    String titleByCode = MEETING_TITLE_BY_ERROR_CODE.get(exception.errorCode());
    if (titleByCode != null) {
      return titleByCode;
    }

    return switch (exception.status()) {
      case FORBIDDEN -> "Доступ запрещен";
      case NOT_FOUND -> "Ресурс не найден";
      case CONFLICT -> "Конфликт запроса";
      case INTERNAL_SERVER_ERROR -> "Ошибка конфигурации";
      default -> "Ошибка выпуска токена";
    };
  }
}
