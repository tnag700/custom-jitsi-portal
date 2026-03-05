package com.acme.jitsi.security;

import java.time.Instant;
import java.net.URL;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.stereotype.Component;

@Component
class OidcClaimsValidator {

  private static final Logger log = LoggerFactory.getLogger(OidcClaimsValidator.class);

  void validate(OidcIdToken idToken, String expectedIssuer, String clientId) {
    if (hasInvalidIssuer(idToken, expectedIssuer)) {
      logInvalidIssuer(expectedIssuer, idToken);
      throw invalidToken("Invalid token issuer");
    }

    if (hasInvalidAudience(idToken, clientId)) {
      logInvalidAudience(clientId, idToken);
      throw invalidToken("Invalid token audience");
    }

    if (isExpired(idToken)) {
      logTokenExpired(idToken);
      throw invalidToken("Token expired");
    }
  }

  private boolean hasInvalidIssuer(OidcIdToken idToken, String expectedIssuer) {
    URL issuer = idToken.getIssuer();
    return issuer == null || !expectedIssuer.equals(issuer.toString());
  }

  private boolean hasInvalidAudience(OidcIdToken idToken, String clientId) {
    List<String> audience = idToken.getAudience();
    return audience == null || !audience.contains(clientId);
  }

  private boolean isExpired(OidcIdToken idToken) {
    Instant expiresAt = idToken.getExpiresAt();
    return expiresAt == null || expiresAt.isBefore(Instant.now());
  }

  private void logInvalidIssuer(String expectedIssuer, OidcIdToken idToken) {
    if (log.isWarnEnabled()) {
      log.warn(
          "oidc_claim_validation_failed reason=invalid_issuer expectedIssuer={} actualIssuer={}",
          expectedIssuer,
          idToken.getIssuer());
    }
  }

  private void logInvalidAudience(String clientId, OidcIdToken idToken) {
    if (log.isWarnEnabled()) {
      log.warn(
          "oidc_claim_validation_failed reason=invalid_audience expectedAudience={} actualAudience={}",
          clientId,
          idToken.getAudience());
    }
  }

  private void logTokenExpired(OidcIdToken idToken) {
    if (log.isWarnEnabled()) {
      log.warn(
          "oidc_claim_validation_failed reason=token_expired expiresAt={}",
          idToken.getExpiresAt());
    }
  }

  private OAuth2AuthenticationException invalidToken(String description) {
    return new OAuth2AuthenticationException(new OAuth2Error("invalid_token", description, null), description);
  }
}