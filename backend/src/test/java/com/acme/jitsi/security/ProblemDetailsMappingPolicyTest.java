package com.acme.jitsi.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.acme.jitsi.domains.meetings.service.MeetingTokenException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class ProblemDetailsMappingPolicyTest {

  private final ProblemDetailsMappingPolicy policy = new DefaultProblemDetailsMappingPolicy();

  @Test
  void mapsAuthErrorEndpointCodeToForbiddenProblemDefinition() {
    ProblemDetailsMappingPolicy.ProblemDefinition definition = policy.mapAuthErrorCode("ACCESS_DENIED");

    assertThat(definition.status()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(definition.errorCode()).isEqualTo("ACCESS_DENIED");
    assertThat(definition.title()).isEqualTo("Вход отклонен");
    assertThat(definition.detail())
        .isEqualTo("Провайдер входа отклонил запрос или аутентификация не завершена.");
  }

  @Test
  void mapsUnknownAuthErrorCodeToAuthRequiredDefinition() {
    ProblemDetailsMappingPolicy.ProblemDefinition definition = policy.mapAuthErrorCode("UNEXPECTED");

    assertThat(definition.status()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(definition.errorCode()).isEqualTo("AUTH_REQUIRED");
    assertThat(definition.title()).isEqualTo("Требуется вход");
    assertThat(definition.detail())
        .isEqualTo("Не удалось выполнить вход. Попробуйте снова или обратитесь в поддержку.");
  }

  @Test
  void mapsSecurityAuthenticationRequired() {
    ProblemDetailsMappingPolicy.ProblemDefinition definition = policy.mapSecurityAuthRequired();

    assertThat(definition.status()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(definition.errorCode()).isEqualTo("AUTH_REQUIRED");
    assertThat(definition.title()).isEqualTo("Требуется вход");
    assertThat(definition.detail())
        .isEqualTo("Сессия отсутствует или истекла. Выполните вход через SSO.");
  }

  @Test
  void mapsSecurityAccessDenied() {
    ProblemDetailsMappingPolicy.ProblemDefinition definition = policy.mapSecurityAccessDenied();

    assertThat(definition.status()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(definition.errorCode()).isEqualTo("ACCESS_DENIED");
    assertThat(definition.title()).isEqualTo("Доступ запрещен");
    assertThat(definition.detail()).isEqualTo("Недостаточно прав для выполнения операции.");
  }

  @Test
  void mapsMeetingTokenExceptionUsingRegistry() {
    MeetingTokenException exception =
        new MeetingTokenException(HttpStatus.CONFLICT, "ROLE_MISMATCH", "Назначение роли неоднозначно.");

    ProblemDetailsMappingPolicy.ProblemDefinition definition = policy.mapMeetingTokenException(exception);

    assertThat(definition.status()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(definition.errorCode()).isEqualTo("ROLE_MISMATCH");
    assertThat(definition.title()).isEqualTo("Конфликт запроса");
    assertThat(definition.detail()).isEqualTo("Назначение роли неоднозначно.");
  }

  @Test
  void mapsValidationErrorCodeByUri() {
    assertThat(policy.resolveValidationErrorCode("/api/v1/auth/refresh")).isEqualTo("INVALID_REQUEST");
    assertThat(policy.resolveValidationErrorCode("/api/v1/invites/exchange")).isEqualTo("INVALID_INVITE");
    assertThat(policy.resolveValidationErrorCode("/api/v1/meetings/meeting-a/access-token"))
        .isEqualTo("INVALID_REQUEST");
  }
}
