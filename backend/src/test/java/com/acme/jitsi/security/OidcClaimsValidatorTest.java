package com.acme.jitsi.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;

class OidcClaimsValidatorTest {

  private final OidcClaimsValidator validator = new OidcClaimsValidator();

  @Test
  void acceptsValidIssuerAudienceAndExpiration() {
    OidcIdToken idToken = new OidcIdToken(
        "token-value",
        Instant.now().minusSeconds(10),
        Instant.now().plusSeconds(300),
        Map.of(
            "iss", "https://issuer.example.test",
            "aud", List.of("jitsi-backend"),
            "sub", "u-1"));

    assertThatCode(() -> validator.validate(idToken, "https://issuer.example.test", "jitsi-backend"))
        .doesNotThrowAnyException();
  }

  @Test
  void rejectsInvalidIssuer() {
    OidcIdToken idToken = new OidcIdToken(
        "token-value",
        Instant.now().minusSeconds(10),
        Instant.now().plusSeconds(300),
        Map.of(
            "iss", "https://unexpected.example.test",
            "aud", List.of("jitsi-backend"),
            "sub", "u-1"));

    assertThatThrownBy(() -> validator.validate(idToken, "https://issuer.example.test", "jitsi-backend"))
        .isInstanceOf(OAuth2AuthenticationException.class)
        .hasMessageContaining("Invalid token issuer");
  }

  @Test
  void rejectsInvalidAudience() {
    OidcIdToken idToken = new OidcIdToken(
        "token-value",
        Instant.now().minusSeconds(10),
        Instant.now().plusSeconds(300),
        Map.of(
            "iss", "https://issuer.example.test",
            "aud", List.of("another-client"),
            "sub", "u-1"));

    assertThatThrownBy(() -> validator.validate(idToken, "https://issuer.example.test", "jitsi-backend"))
        .isInstanceOf(OAuth2AuthenticationException.class)
        .hasMessageContaining("Invalid token audience");
  }

  @Test
  void rejectsExpiredToken() {
    OidcIdToken idToken = new OidcIdToken(
        "token-value",
        Instant.now().minusSeconds(600),
        Instant.now().minusSeconds(10),
        Map.of(
            "iss", "https://issuer.example.test",
            "aud", List.of("jitsi-backend"),
            "sub", "u-1"));

    assertThatThrownBy(() -> validator.validate(idToken, "https://issuer.example.test", "jitsi-backend"))
        .isInstanceOf(OAuth2AuthenticationException.class)
        .hasMessageContaining("Token expired");
  }

  @Test
  void rejectsTokenWithoutIssuerClaim() {
    OidcIdToken idToken = new OidcIdToken(
        "token-value",
        Instant.now().minusSeconds(10),
        Instant.now().plusSeconds(300),
        Map.of(
            "aud", List.of("jitsi-backend"),
            "sub", "u-1"));

    assertThatThrownBy(() -> validator.validate(idToken, "https://issuer.example.test", "jitsi-backend"))
        .isInstanceOf(OAuth2AuthenticationException.class)
        .hasMessageContaining("Invalid token issuer");
  }

  @Test
  void rejectsTokenWithoutAudienceClaim() {
    OidcIdToken idToken = new OidcIdToken(
        "token-value",
        Instant.now().minusSeconds(10),
        Instant.now().plusSeconds(300),
        Map.of(
            "iss", "https://issuer.example.test",
            "sub", "u-1"));

    assertThatThrownBy(() -> validator.validate(idToken, "https://issuer.example.test", "jitsi-backend"))
        .isInstanceOf(OAuth2AuthenticationException.class)
        .hasMessageContaining("Invalid token audience");
  }
}
