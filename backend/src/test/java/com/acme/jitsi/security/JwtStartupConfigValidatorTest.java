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
        .isEqualTo("CONFIG_MISSING_REQUIRED");
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
        .isEqualTo("JWT_CONFIG_MISMATCH");
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
        .isEqualTo("CONFIG_INCOMPATIBLE");
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
        .isEqualTo("CONFIG_INCOMPATIBLE");
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
        .isEqualTo("CONFIG_INCOMPATIBLE");
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
        .isEqualTo("CONFIG_INCOMPATIBLE");
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
        .isEqualTo("CONFIG_INCOMPATIBLE");
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
                    assertThat(exception.errorCode()).isEqualTo("CONFIG_INCOMPATIBLE");
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
        .isEqualTo("JWT_CONFIG_MISMATCH");
  }
}