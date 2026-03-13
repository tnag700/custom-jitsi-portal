package com.acme.jitsi.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class JwtStartupConfigValidatorTest {

    private static final DefaultJwtAlgorithmPolicy DEFAULT_JWT_ALGORITHM_POLICY = new DefaultJwtAlgorithmPolicy();

  @Test
  void passesForValidConsistentConfiguration() {
    JwtStartupConfigValidator validator = new JwtStartupConfigValidator(
        DEFAULT_JWT_ALGORITHM_POLICY,
        "https://portal.example.test",
        "jitsi-meet",
        "role",
        "HS256",
        20,
        60,
        "01234567890123456789012345678901",
        "",
        "https://portal.example.test",
        "jitsi-meet",
        "role",
        "HS256",
        20,
        60);

    assertThatCode(validator::validateOrThrow).doesNotThrowAnyException();
  }

  @Test
  void failsWithMissingRequiredErrorCodeWhenIssuerMissing() {
    JwtStartupConfigValidator validator = new JwtStartupConfigValidator(
        DEFAULT_JWT_ALGORITHM_POLICY,
        "",
        "jitsi-meet",
        "role",
        "HS256",
        20,
        60,
        "01234567890123456789012345678901",
        "",
        "https://portal.example.test",
        "jitsi-meet",
        "role",
        "HS256",
        20,
        60);

    assertThatThrownBy(validator::validateOrThrow)
        .isInstanceOf(JwtStartupValidationException.class)
        .extracting(error -> ((JwtStartupValidationException) error).errorCode())
        .isEqualTo(JwtStartupValidationErrorCode.CONFIG_MISSING_REQUIRED.name());
  }

  @Test
  void failsWithMismatchErrorCodeWhenIssuerDiffersFromContour() {
    JwtStartupConfigValidator validator = new JwtStartupConfigValidator(
        DEFAULT_JWT_ALGORITHM_POLICY,
        "https://portal-a.example.test",
        "jitsi-meet",
        "role",
        "HS256",
        20,
        60,
        "01234567890123456789012345678901",
        "",
        "https://portal-b.example.test",
        "jitsi-meet",
        "role",
        "HS256",
        20,
        60);

    assertThatThrownBy(validator::validateOrThrow)
        .isInstanceOf(JwtStartupValidationException.class)
        .extracting(error -> ((JwtStartupValidationException) error).errorCode())
        .isEqualTo(JwtStartupValidationErrorCode.JWT_CONFIG_MISMATCH.name());
  }

  @Test
  void failsWithConfigIncompatibleWhenAlgorithmUnsupported() {
    JwtStartupConfigValidator validator = new JwtStartupConfigValidator(
        DEFAULT_JWT_ALGORITHM_POLICY,
        "https://portal.example.test",
        "jitsi-meet",
        "role",
        "RS256",
        20,
        60,
        "01234567890123456789012345678901",
        "",
        "https://portal.example.test",
        "jitsi-meet",
        "role",
        "RS256",
        20,
        60);

    assertThatThrownBy(validator::validateOrThrow)
        .isInstanceOf(JwtStartupValidationException.class)
        .extracting(error -> ((JwtStartupValidationException) error).errorCode())
        .isEqualTo(JwtStartupValidationErrorCode.CONFIG_INCOMPATIBLE.name());
  }

  @Test
  void failsWithConfigIncompatibleWhenAccessTtlIsNotLessThanRefreshTtl() {
    JwtStartupConfigValidator validator = new JwtStartupConfigValidator(
        DEFAULT_JWT_ALGORITHM_POLICY,
        "https://portal.example.test",
        "jitsi-meet",
        "role",
        "HS256",
        60,
        60,
        "01234567890123456789012345678901",
        "",
        "https://portal.example.test",
        "jitsi-meet",
        "role",
        "HS256",
        60,
        60);

    assertThatThrownBy(validator::validateOrThrow)
        .isInstanceOf(JwtStartupValidationException.class)
        .extracting(error -> ((JwtStartupValidationException) error).errorCode())
        .isEqualTo(JwtStartupValidationErrorCode.CONFIG_INCOMPATIBLE.name());
  }

  @Test
  void failsWithConfigIncompatibleWhenBothSecretAndJwksConfigured() {
    JwtStartupConfigValidator validator = new JwtStartupConfigValidator(
        DEFAULT_JWT_ALGORITHM_POLICY,
        "https://portal.example.test",
        "jitsi-meet",
        "role",
        "HS256",
        20,
        60,
        "01234567890123456789012345678901",
        "https://idp.example.test/.well-known/jwks.json",
        "https://portal.example.test",
        "jitsi-meet",
        "role",
        "HS256",
        20,
        60);

    assertThatThrownBy(validator::validateOrThrow)
        .isInstanceOf(JwtStartupValidationException.class)
        .extracting(error -> ((JwtStartupValidationException) error).errorCode())
        .isEqualTo(JwtStartupValidationErrorCode.CONFIG_INCOMPATIBLE.name());
  }

  @Test
  void failsWithConfigIncompatibleWhenDefaultPlaceholderSecretUsed() {
    JwtStartupConfigValidator validator = new JwtStartupConfigValidator(
        DEFAULT_JWT_ALGORITHM_POLICY,
        "https://portal.example.test",
        "jitsi-meet",
        "role",
        "HS256",
        20,
        60,
        "change-me-change-me-change-me-1234",
        "",
        "https://portal.example.test",
        "jitsi-meet",
        "role",
        "HS256",
        20,
        60);

    assertThatThrownBy(validator::validateOrThrow)
        .isInstanceOf(JwtStartupValidationException.class)
        .extracting(error -> ((JwtStartupValidationException) error).errorCode())
        .isEqualTo(JwtStartupValidationErrorCode.CONFIG_INCOMPATIBLE.name());
  }

  @Test
  void failsWithConfigIncompatibleWhenJwksSourceUsesSymmetricAlgorithm() {
    JwtStartupConfigValidator validator = new JwtStartupConfigValidator(
        DEFAULT_JWT_ALGORITHM_POLICY,
        "https://portal.example.test",
        "jitsi-meet",
        "role",
        "HS256",
        20,
        60,
        "",
        "https://idp.example.test/.well-known/jwks.json",
        "https://portal.example.test",
        "jitsi-meet",
        "role",
        "HS256",
        20,
        60);

    assertThatThrownBy(validator::validateOrThrow)
        .isInstanceOf(JwtStartupValidationException.class)
        .extracting(error -> ((JwtStartupValidationException) error).errorCode())
        .isEqualTo(JwtStartupValidationErrorCode.CONFIG_INCOMPATIBLE.name());
  }

    @Test
    void failsWithConfigIncompatibleWhenJwksSourceUsesAsymmetricAlgorithmInCurrentRuntime() {
        JwtStartupConfigValidator validator = new JwtStartupConfigValidator(
                DEFAULT_JWT_ALGORITHM_POLICY,
                "https://portal.example.test",
                "jitsi-meet",
                "role",
                "RS256",
                20,
                60,
                "",
                "https://idp.example.test/.well-known/jwks.json",
                "https://portal.example.test",
                "jitsi-meet",
                "role",
                "RS256",
                20,
                60);

        assertThatThrownBy(validator::validateOrThrow)
                .isInstanceOf(JwtStartupValidationException.class)
                .satisfies(error -> {
                    JwtStartupValidationException exception = (JwtStartupValidationException) error;
                    assertThat(exception.errorCode()).isEqualTo(JwtStartupValidationErrorCode.CONFIG_INCOMPATIBLE.name());
                    assertThat(exception.getMessage()).contains("key source jwks is not supported");
                });
    }

  @Test
  void failsWithMismatchErrorCodeWhenRoleClaimDiffersFromContour() {
    JwtStartupConfigValidator validator = new JwtStartupConfigValidator(
        DEFAULT_JWT_ALGORITHM_POLICY,
        "https://portal.example.test",
        "jitsi-meet",
        "roleA",
        "HS256",
        20,
        60,
        "01234567890123456789012345678901",
        "",
        "https://portal.example.test",
        "jitsi-meet",
        "roleB",
        "HS256",
        20,
        60);

    assertThatThrownBy(validator::validateOrThrow)
        .isInstanceOf(JwtStartupValidationException.class)
        .extracting(error -> ((JwtStartupValidationException) error).errorCode())
        .isEqualTo(JwtStartupValidationErrorCode.JWT_CONFIG_MISMATCH.name());
  }
}