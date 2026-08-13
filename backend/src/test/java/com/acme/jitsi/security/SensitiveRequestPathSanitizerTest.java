package com.acme.jitsi.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SensitiveRequestPathSanitizerTest {

  @ParameterizedTest
  @ValueSource(strings = {
      "/api/v1/invites/secret-token/validate",
      "/API/V1/INVITES/secret-token/VALIDATE",
      "/api/v1/%69nvites/secret-token/validate",
      "/api/v1/%2569nvites/secret-token/validate"
  })
  void redactsRetiredTokenBearingValidationPaths(String requestUri) {
    assertThat(SensitiveRequestPathSanitizer.sanitize(requestUri))
        .isEqualTo("/api/v1/invites/[redacted]/validate");
  }

  @ParameterizedTest
  @ValueSource(strings = {
      "/api/v1/invites/validate",
      "/api/v1/invites/exchange",
      "/api/v1/invites/not-a-legacy-route",
      "/api/v1/rooms"
  })
  void preservesNonSensitiveRequestPaths(String requestUri) {
    assertThat(SensitiveRequestPathSanitizer.sanitize(requestUri)).isEqualTo(requestUri);
  }
}
