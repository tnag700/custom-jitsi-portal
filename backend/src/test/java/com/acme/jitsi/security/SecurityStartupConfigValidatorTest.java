package com.acme.jitsi.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SecurityStartupConfigValidatorTest {

  @Test
  void passesForValidFrontendOriginAndNonPlaceholderSecret() {
    SecurityStartupConfigValidator validator =
        new SecurityStartupConfigValidator("https://portal.example.com", "real-secret");

    assertThatCode(validator::afterPropertiesSet).doesNotThrowAnyException();
  }

  @Test
  void passesForHttpLocalhostOrigin() {
    SecurityStartupConfigValidator validator =
        new SecurityStartupConfigValidator("http://localhost:3000", "real-secret");

    assertThatCode(validator::afterPropertiesSet).doesNotThrowAnyException();
  }

  @Test
  void failsWhenFrontendOriginContainsWildcard() {
    SecurityStartupConfigValidator validator =
        new SecurityStartupConfigValidator("https://*.example.com", "real-secret");

    assertThatThrownBy(validator::afterPropertiesSet)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("must not contain wildcards");
  }

  @Test
  void failsWhenFrontendOriginContainsPath() {
    SecurityStartupConfigValidator validator =
        new SecurityStartupConfigValidator("https://portal.example.com/auth", "real-secret");

    assertThatThrownBy(validator::afterPropertiesSet)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("must not include a path");
  }

  @Test
  void failsWhenSsoClientSecretUsesPlaceholder() {
    SecurityStartupConfigValidator validator =
        new SecurityStartupConfigValidator("https://portal.example.com", "change-me");

    assertThatThrownBy(validator::afterPropertiesSet)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Insecure SSO client secret placeholder");
  }

  @Test
  void failsWhenFrontendOriginUsesHttpOutsideLocalhost() {
    SecurityStartupConfigValidator validator =
        new SecurityStartupConfigValidator("http://portal.example.com", "real-secret");

    assertThatThrownBy(validator::afterPropertiesSet)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("must use https outside localhost");
  }
}
