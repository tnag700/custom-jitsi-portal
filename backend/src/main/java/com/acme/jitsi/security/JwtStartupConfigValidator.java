package com.acme.jitsi.security;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
class JwtStartupConfigValidator implements JwtStartupValidationPolicy {

  private static final String INSECURE_SECRET_PLACEHOLDER = "change-me-change-me-change-me-1234";

  private final JwtAlgorithmPolicy algorithmPolicy;

  private final String meetingsIssuer;
  private final String meetingsAudience;
  private final String meetingsRoleClaim;
  private final String meetingsAlgorithm;
  private final int meetingsAccessTtlMinutes;
  private final int meetingsRefreshTtlMinutes;
  private final String meetingsSigningSecret;
  private final String meetingsJwksUri;

  private final String contourIssuer;
  private final String contourAudience;
  private final String contourRoleClaim;
  private final String contourAlgorithm;
  private final int contourAccessTtlMinutes;
  private final int contourRefreshTtlMinutes;

  JwtStartupConfigValidator(
      JwtAlgorithmPolicy algorithmPolicy,
      @Value("${app.meetings.token.issuer:}") String meetingsIssuer,
      @Value("${app.meetings.token.audience:}") String meetingsAudience,
      @Value("${app.meetings.token.role-claim-name:}") String meetingsRoleClaim,
      @Value("${app.meetings.token.algorithm:HS256}") String meetingsAlgorithm,
      @Value("${app.meetings.token.ttl-minutes:0}") int meetingsAccessTtlMinutes,
      @Value("${app.auth.refresh.idle-ttl-minutes:0}") int meetingsRefreshTtlMinutes,
      @Value("${app.meetings.token.signing-secret:}") String meetingsSigningSecret,
      @Value("${app.meetings.token.jwks-uri:}") String meetingsJwksUri,
      @Value("${app.security.jwt-contour.issuer:}") String contourIssuer,
      @Value("${app.security.jwt-contour.audience:}") String contourAudience,
      @Value("${app.security.jwt-contour.role-claim:}") String contourRoleClaim,
      @Value("${app.security.jwt-contour.algorithm:}") String contourAlgorithm,
      @Value("${app.security.jwt-contour.access-ttl-minutes:0}") int contourAccessTtlMinutes,
      @Value("${app.security.jwt-contour.refresh-ttl-minutes:0}") int contourRefreshTtlMinutes) {
    this.algorithmPolicy = algorithmPolicy;
    this.meetingsIssuer = meetingsIssuer;
    this.meetingsAudience = meetingsAudience;
    this.meetingsRoleClaim = meetingsRoleClaim;
    this.meetingsAlgorithm = meetingsAlgorithm;
    this.meetingsAccessTtlMinutes = meetingsAccessTtlMinutes;
    this.meetingsRefreshTtlMinutes = meetingsRefreshTtlMinutes;
    this.meetingsSigningSecret = meetingsSigningSecret;
    this.meetingsJwksUri = meetingsJwksUri;
    this.contourIssuer = contourIssuer;
    this.contourAudience = contourAudience;
    this.contourRoleClaim = contourRoleClaim;
    this.contourAlgorithm = contourAlgorithm;
    this.contourAccessTtlMinutes = contourAccessTtlMinutes;
    this.contourRefreshTtlMinutes = contourRefreshTtlMinutes;
  }

  @Override
  public void validateOrThrow() {
    List<Violation> violations = new ArrayList<>();

    requireNotBlank(meetingsIssuer, "app.meetings.token.issuer", violations);
    requireNotBlank(meetingsAudience, "app.meetings.token.audience", violations);
    requireNotBlank(meetingsRoleClaim, "app.meetings.token.role-claim-name", violations);
    requireNotBlank(meetingsAlgorithm, "app.meetings.token.algorithm", violations);

    requireNotBlank(contourIssuer, "app.security.jwt-contour.issuer", violations);
    requireNotBlank(contourAudience, "app.security.jwt-contour.audience", violations);
    requireNotBlank(contourRoleClaim, "app.security.jwt-contour.role-claim", violations);
    requireNotBlank(contourAlgorithm, "app.security.jwt-contour.algorithm", violations);

    JwtKeySource keySource = validateKeySource(violations);
    validateSecretQualityIfUsed(keySource, violations);
    validateTtl("app.meetings.token.ttl-minutes", meetingsAccessTtlMinutes, violations);
    validateTtl("app.auth.refresh.idle-ttl-minutes", meetingsRefreshTtlMinutes, violations);
    validateTtlOrder(meetingsAccessTtlMinutes, meetingsRefreshTtlMinutes, "app.meetings + app.auth.refresh", violations);

    validateTtl("app.security.jwt-contour.access-ttl-minutes", contourAccessTtlMinutes, violations);
    validateTtl("app.security.jwt-contour.refresh-ttl-minutes", contourRefreshTtlMinutes, violations);
    validateTtlOrder(contourAccessTtlMinutes, contourRefreshTtlMinutes, "app.security.jwt-contour", violations);

    validateSupportedAlgorithm(meetingsAlgorithm, "app.meetings.token.algorithm", keySource, violations);
    validateSupportedAlgorithm(contourAlgorithm, "app.security.jwt-contour.algorithm", keySource, violations);

    requireMatch("issuer", meetingsIssuer, contourIssuer, violations);
    requireMatch("audience", meetingsAudience, contourAudience, violations);
    requireMatch("role-claim", meetingsRoleClaim, contourRoleClaim, violations);
    requireMatch("algorithm", normalize(meetingsAlgorithm), normalize(contourAlgorithm), violations);
    requireMatch("access-ttl-minutes", meetingsAccessTtlMinutes, contourAccessTtlMinutes, violations);
    requireMatch("refresh-ttl-minutes", meetingsRefreshTtlMinutes, contourRefreshTtlMinutes, violations);

    if (!violations.isEmpty()) {
      Violation primary = violations.getFirst();
      JwtStartupValidationErrorCode primaryErrorCode = primary.errorCode;
      String primaryMessage = primary.message;
      String details = joinViolationMessages(violations, primaryMessage);
      throw new JwtStartupValidationException(primaryErrorCode, details);
    }
  }

  private String joinViolationMessages(List<Violation> violations, String fallback) {
    if (violations.isEmpty()) {
      return fallback;
    }

    StringBuilder detailsBuilder = new StringBuilder();
    for (Violation violation : violations) {
      if (!detailsBuilder.isEmpty()) {
        detailsBuilder.append(" | ");
      }
      detailsBuilder.append(violation.message);
    }
    return detailsBuilder.toString();
  }

  private JwtKeySource validateKeySource(List<Violation> violations) {
    boolean hasSecret = meetingsSigningSecret != null && !meetingsSigningSecret.isBlank();
    boolean hasJwks = meetingsJwksUri != null && !meetingsJwksUri.isBlank();
    if (hasSecret && hasJwks) {
      addViolation(
          violations,
          JwtStartupValidationErrorCode.CONFIG_INCOMPATIBLE,
          "JWT key source conflict: configure exactly one of app.meetings.token.signing-secret or app.meetings.token.jwks-uri.");
      return null;
    }
    if (!hasSecret && !hasJwks) {
      addViolation(
          violations,
          JwtStartupValidationErrorCode.CONFIG_MISSING_REQUIRED,
          "Missing required JWT key source: configure app.meetings.token.signing-secret or app.meetings.token.jwks-uri.");
      return null;
    }
    return hasSecret ? JwtKeySource.SECRET : JwtKeySource.JWKS;
  }

  private void validateSupportedAlgorithm(
      String algorithm,
      String propertyName,
      JwtKeySource keySource,
      List<Violation> violations) {
    if (keySource == null) {
      return;
    }
    String normalized = normalize(algorithm);
    Set<String> supported = algorithmPolicy.supportedAlgorithmsForKeySource(keySource);
    List<String> supportedAlgorithms = new ArrayList<>(supported);
    if (supportedAlgorithms.isEmpty()) {
      String keySourceValue = toLowerCaseName(keySource);
      addViolation(
          violations,
          JwtStartupValidationErrorCode.CONFIG_INCOMPATIBLE,
          "JWT key source " + keySourceValue
              + " is not supported in current runtime configuration.");
      return;
    }

    if (!algorithmPolicy.isSupportedForKeySource(normalized, keySource)) {
      String keySourceValue = toLowerCaseName(keySource);
      addViolation(
          violations,
          JwtStartupValidationErrorCode.CONFIG_INCOMPATIBLE,
          "Unsupported JWT algorithm in " + propertyName + " for key source " + keySourceValue
              + ": " + normalized + ". Supported: " + supportedAlgorithms + ".");
    }
  }

  private String toLowerCaseName(JwtKeySource keySource) {
    return switch (keySource) {
      case SECRET -> "secret";
      case JWKS -> "jwks";
    };
  }

  private void validateSecretQualityIfUsed(JwtKeySource keySource, List<Violation> violations) {
    if (keySource != JwtKeySource.SECRET) {
      return;
    }
    String normalizedSecret = meetingsSigningSecret == null ? "" : new String(meetingsSigningSecret.trim());
    if (INSECURE_SECRET_PLACEHOLDER.equals(normalizedSecret)) {
      addViolation(
          violations,
          JwtStartupValidationErrorCode.CONFIG_INCOMPATIBLE,
          "Insecure default JWT signing secret detected. Configure APP_MEETINGS_TOKEN_SIGNING_SECRET with a non-default value.");
    }
  }

  private void validateTtl(String propertyName, int ttlMinutes, List<Violation> violations) {
    if (ttlMinutes <= 0) {
      addViolation(
          violations,
          JwtStartupValidationErrorCode.CONFIG_MISSING_REQUIRED,
          "Missing required positive TTL in " + propertyName + ".");
    }
  }

  private void validateTtlOrder(int accessTtl, int refreshTtl, String scope, List<Violation> violations) {
    if (accessTtl >= refreshTtl) {
      addViolation(
          violations,
          JwtStartupValidationErrorCode.CONFIG_INCOMPATIBLE,
          "JWT TTL mismatch in " + scope + ": access TTL must be less than refresh TTL.");
    }
  }

  private void requireNotBlank(String value, String propertyName, List<Violation> violations) {
    if (value == null || value.isBlank()) {
      addViolation(
          violations,
          JwtStartupValidationErrorCode.CONFIG_MISSING_REQUIRED,
          "Missing required JWT parameter: " + propertyName + ".");
    }
  }

  private void requireMatch(String fieldName, Object sourceValue, Object contourValue, List<Violation> violations) {
    if (!Objects.equals(sourceValue, contourValue)) {
      addViolation(
          violations,
          JwtStartupValidationErrorCode.JWT_CONFIG_MISMATCH,
          "JWT contour mismatch for " + fieldName + ": source and contour values must match.");
    }
  }

  private void addViolation(
      List<Violation> violations,
      JwtStartupValidationErrorCode errorCode,
      String message) {
    violations.add(new Violation(errorCode, message));
  }

  private String normalize(String value) {
    if (value == null) {
      return "";
    }
    String trimmed = value.trim();
    String localTrimmed = new String(trimmed);
    return localTrimmed.toUpperCase(Locale.ROOT);
  }

  private static final class Violation {
    private final JwtStartupValidationErrorCode errorCode;
    private final String message;

    private Violation(JwtStartupValidationErrorCode errorCode, String message) {
      this.errorCode = errorCode;
      this.message = message;
    }
  }
}