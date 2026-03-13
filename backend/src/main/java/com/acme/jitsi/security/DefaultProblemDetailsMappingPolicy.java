package com.acme.jitsi.security;

import com.acme.jitsi.shared.ErrorCode;
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
          ErrorCode.AUTH_REQUIRED.code());

  private static final ProblemDefinition ACCESS_DENIED_FOR_AUTH_ERROR =
      new ProblemDefinition(
          HttpStatus.FORBIDDEN,
          "Вход отклонен",
          "Провайдер входа отклонил запрос или аутентификация не завершена.",
          ErrorCode.ACCESS_DENIED.code());

  private static final ProblemDefinition SECURITY_AUTH_REQUIRED =
      new ProblemDefinition(
          HttpStatus.UNAUTHORIZED,
          "Требуется вход",
          "Сессия отсутствует или истекла. Выполните вход через SSO.",
          ErrorCode.AUTH_REQUIRED.code());

  private static final ProblemDefinition SECURITY_ACCESS_DENIED =
      new ProblemDefinition(
          HttpStatus.FORBIDDEN,
          "Доступ запрещен",
          "Недостаточно прав для выполнения операции.",
          ErrorCode.ACCESS_DENIED.code());

  private static final Map<String, String> MEETING_TITLE_BY_ERROR_CODE =
      Map.of(
          ErrorCode.INVITE_EXHAUSTED.code(), "Инвайт исчерпан",
          ErrorCode.INVITE_EXPIRED.code(), "Инвайт просрочен",
          ErrorCode.INVITE_REVOKED.code(), "Инвайт отозван",
          ErrorCode.ROOM_CLOSED.code(), "Встреча недоступна",
          ErrorCode.MEETING_NOT_FOUND.code(), "Встреча не найдена",
          ErrorCode.MEETING_CANCELED.code(), "Встреча отменена",
          ErrorCode.MEETING_ENDED.code(), "Встреча завершена",
          ErrorCode.MEETING_FINALIZED.code(), "Встреча финализирована",
          ErrorCode.INVITE_NOT_FOUND.code(), "Инвайт не найден");

  @Override
  public ProblemDefinition mapAuthErrorCode(String code) {
    if (ErrorCode.ACCESS_DENIED.code().equals(code)) {
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
    public ProblemDefinition mapTokenException(HttpStatus status, String errorCode, String detail) {
    String sanitizedDetail = detail;
    if (ErrorCode.CONFIG_INCOMPATIBLE.code().equals(errorCode)) {
      sanitizedDetail = "Выпуск токена временно недоступен из-за несовместимой активной конфигурации.";
    }
    return new ProblemDefinition(
      status,
      resolveTokenTitle(status, errorCode),
      sanitizedDetail,
      errorCode);
  }

  @Override
  public String resolveValidationErrorCode(String requestUri) {
    if (requestUri != null && requestUri.startsWith("/api/v1/profile/")) {
      return ErrorCode.PROFILE_VALIDATION_FAILED.code();
    }
    if (requestUri != null && requestUri.startsWith("/api/v1/invites/")) {
      return ErrorCode.INVALID_INVITE.code();
    }
    return ErrorCode.INVALID_REQUEST.code();
  }

  private String resolveTokenTitle(HttpStatus status, String errorCode) {
    String titleByCode = MEETING_TITLE_BY_ERROR_CODE.get(errorCode);
    if (titleByCode != null) {
      return titleByCode;
    }

    return switch (status) {
      case FORBIDDEN -> "Доступ запрещен";
      case NOT_FOUND -> "Ресурс не найден";
      case CONFLICT -> "Конфликт запроса";
      case INTERNAL_SERVER_ERROR -> "Ошибка конфигурации";
      default -> "Ошибка выпуска токена";
    };
  }
}
