package com.acme.jitsi.security;

import com.acme.jitsi.shared.ErrorCode;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class ProblemDetailsMappingPolicyTest {

  private final ProblemDetailsMappingPolicy policy = new DefaultProblemDetailsMappingPolicy();

  @Test
  void mapsAuthErrorEndpointCodeToForbiddenProblemDefinition() {
    ProblemDetailsMappingPolicy.ProblemDefinition definition = policy.mapAuthErrorCode(ErrorCode.ACCESS_DENIED.code());

    assertThat(definition.status()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(definition.errorCode()).isEqualTo(ErrorCode.ACCESS_DENIED.code());
    assertThat(definition.title()).isEqualTo("Вход отклонен");
    assertThat(definition.detail())
        .isEqualTo("Провайдер входа отклонил запрос или аутентификация не завершена.");
  }

  @Test
  void mapsUnknownAuthErrorCodeToAuthRequiredDefinition() {
    ProblemDetailsMappingPolicy.ProblemDefinition definition = policy.mapAuthErrorCode("UNEXPECTED");

    assertThat(definition.status()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(definition.errorCode()).isEqualTo(ErrorCode.AUTH_REQUIRED.code());
    assertThat(definition.title()).isEqualTo("Требуется вход");
    assertThat(definition.detail())
        .isEqualTo("Не удалось выполнить вход. Попробуйте снова или обратитесь в поддержку.");
  }

  @Test
  void mapsSecurityAuthenticationRequired() {
    ProblemDetailsMappingPolicy.ProblemDefinition definition = policy.mapSecurityAuthRequired();

    assertThat(definition.status()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(definition.errorCode()).isEqualTo(ErrorCode.AUTH_REQUIRED.code());
    assertThat(definition.title()).isEqualTo("Требуется вход");
    assertThat(definition.detail())
        .isEqualTo("Сессия отсутствует или истекла. Выполните вход через SSO.");
  }

  @Test
  void mapsSecurityAccessDenied() {
    ProblemDetailsMappingPolicy.ProblemDefinition definition = policy.mapSecurityAccessDenied();

    assertThat(definition.status()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(definition.errorCode()).isEqualTo(ErrorCode.ACCESS_DENIED.code());
    assertThat(definition.title()).isEqualTo("Доступ запрещен");
    assertThat(definition.detail()).isEqualTo("Недостаточно прав для выполнения операции.");
  }

  @Test
  void mapsTokenExceptionUsingRegistry() {
    ProblemDetailsMappingPolicy.ProblemDefinition definition =
        policy.mapTokenException(HttpStatus.CONFLICT, ErrorCode.ROLE_MISMATCH.code(), "Назначение роли неоднозначно.");

    assertThat(definition.status()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(definition.errorCode()).isEqualTo(ErrorCode.ROLE_MISMATCH.code());
    assertThat(definition.title()).isEqualTo("Конфликт запроса");
    assertThat(definition.detail()).isEqualTo("Назначение роли неоднозначно.");
  }

  @Test
  void sanitizesConfigIncompatibleTokenDetail() {
    ProblemDetailsMappingPolicy.ProblemDefinition definition = policy.mapTokenException(
        HttpStatus.INTERNAL_SERVER_ERROR,
        ErrorCode.CONFIG_INCOMPATIBLE.code(),
        "Token issuance is blocked due to incompatible active config set: cs-1");

    assertThat(definition.status()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    assertThat(definition.errorCode()).isEqualTo(ErrorCode.CONFIG_INCOMPATIBLE.code());
    assertThat(definition.title()).isEqualTo("Ошибка конфигурации");
    assertThat(definition.detail())
        .isEqualTo("Выпуск токена временно недоступен из-за несовместимой активной конфигурации.")
        .doesNotContain("cs-1");
  }

  @Test
  void mapsValidationErrorCodeByUri() {
    assertThat(policy.resolveValidationErrorCode("/api/v1/auth/refresh")).isEqualTo(ErrorCode.INVALID_REQUEST.code());
    assertThat(policy.resolveValidationErrorCode("/api/v1/invites/exchange")).isEqualTo(ErrorCode.INVALID_INVITE.code());
    assertThat(policy.resolveValidationErrorCode("/api/v1/meetings/meeting-a/access-token"))
        .isEqualTo(ErrorCode.INVALID_REQUEST.code());
  }
}


