package com.acme.jitsi.domains.configsets.service;

import com.acme.jitsi.security.JwtAlgorithmPolicy;
import com.acme.jitsi.security.JwtKeySource;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ConfigSetDryRunValidator {

  private final JwtAlgorithmPolicy jwtAlgorithmPolicy;
  private final String contourIssuer;
  private final String contourAudience;
  private final String contourRoleClaim;
  private final String meetingsIssuer;
  private final String meetingsAudience;
  private final String meetingsRoleClaim;
  private final String expectedMeetingsServiceUrl;
  private final String expectedMeetingsApiVersion;

  public ConfigSetDryRunValidator(
      JwtAlgorithmPolicy jwtAlgorithmPolicy,
      @Value("${app.security.jwt-contour.issuer:}") String contourIssuer,
      @Value("${app.security.jwt-contour.audience:}") String contourAudience,
      @Value("${app.security.jwt-contour.role-claim:}") String contourRoleClaim,
      @Value("${app.meetings.token.issuer:}") String meetingsIssuer,
      @Value("${app.meetings.token.audience:}") String meetingsAudience,
      @Value("${app.meetings.token.role-claim-name:}") String meetingsRoleClaim,
      @Value("${app.meetings.service-url:}") String expectedMeetingsServiceUrl,
      @Value("${app.api.version:v1}") String expectedMeetingsApiVersion) {
    this.jwtAlgorithmPolicy = jwtAlgorithmPolicy;
    this.contourIssuer = contourIssuer;
    this.contourAudience = contourAudience;
    this.contourRoleClaim = contourRoleClaim;
    this.meetingsIssuer = meetingsIssuer;
    this.meetingsAudience = meetingsAudience;
    this.meetingsRoleClaim = meetingsRoleClaim;
    this.expectedMeetingsServiceUrl = expectedMeetingsServiceUrl;
    this.expectedMeetingsApiVersion = expectedMeetingsApiVersion;
  }

  public DryRunResult validate(ConfigSet configSet) {
    ConfigCompatibilityCheckResult compatibilityResult = validateCompatibility(configSet, null);
    return DryRunResult.fromCompatibilityResult(compatibilityResult);
  }

  public ConfigCompatibilityCheckResult validateCompatibility(ConfigSet configSet, String traceId) {
    List<ConfigCompatibilityMismatch> mismatches = new ArrayList<>();
    if (configSet == null) {
      mismatches.add(new ConfigCompatibilityMismatch(
          ConfigCompatibilityMismatchCode.ENDPOINT_MISMATCH,
          "Config set must be provided",
          "non-null configSet",
          "null"));
      return new ConfigCompatibilityCheckResult(false, mismatches, Instant.now(), resolveTraceId(traceId));
    }

    requireNonBlank(configSet.issuer(), "issuer", mismatches);
    requireNonBlank(configSet.audience(), "audience", mismatches);
    requireNonBlank(configSet.algorithm(), "algorithm", mismatches);
    requireNonBlank(configSet.roleClaim(), "roleClaim", mismatches);
    requireNonBlank(configSet.meetingsServiceUrl(), "meetingsServiceUrl", mismatches);
    if (configSet.accessTtlMinutes() <= 0) {
      mismatches.add(new ConfigCompatibilityMismatch(
          ConfigCompatibilityMismatchCode.ALGORITHM_KEY_SOURCE_MISMATCH,
          "accessTtlMinutes must be > 0",
          "> 0",
          String.valueOf(configSet.accessTtlMinutes())));
    }

    String algorithm = normalizeAlgorithm(configSet.algorithm());
    if (algorithm.startsWith("HS")) {
      if (isBlank(configSet.signingSecret())) {
        mismatches.add(new ConfigCompatibilityMismatch(
            ConfigCompatibilityMismatchCode.ALGORITHM_KEY_SOURCE_MISMATCH,
            "signingSecret is required for HS* algorithms",
            "signingSecret configured",
            "missing"));
      }
      if (!jwtAlgorithmPolicy.isSupportedForKeySource(algorithm, JwtKeySource.SECRET)) {
        mismatches.add(new ConfigCompatibilityMismatch(
            ConfigCompatibilityMismatchCode.ALGORITHM_KEY_SOURCE_MISMATCH,
            "algorithm is not supported for secret-based JWT",
            "supported for SECRET",
            algorithm));
      }
    } else if (algorithm.startsWith("RS")) {
      if (isBlank(configSet.jwksUri())) {
        mismatches.add(new ConfigCompatibilityMismatch(
            ConfigCompatibilityMismatchCode.ALGORITHM_KEY_SOURCE_MISMATCH,
            "jwksUri is required for RS* algorithms",
            "jwksUri configured",
            "missing"));
      }
      if (!jwtAlgorithmPolicy.isSupportedForKeySource(algorithm, JwtKeySource.JWKS)) {
        mismatches.add(new ConfigCompatibilityMismatch(
            ConfigCompatibilityMismatchCode.ALGORITHM_KEY_SOURCE_MISMATCH,
            "algorithm is not supported for jwks-based JWT",
            "supported for JWKS",
            algorithm));
      }
    } else {
      mismatches.add(new ConfigCompatibilityMismatch(
          ConfigCompatibilityMismatchCode.ALGORITHM_KEY_SOURCE_MISMATCH,
          "Unsupported algorithm: " + configSet.algorithm(),
          "HS* or RS*",
          configSet.algorithm()));
    }

    compareIfConfigured(
        ConfigCompatibilityMismatchCode.ISSUER_MISMATCH,
        configSet.issuer(),
        meetingsIssuer,
        "issuer mismatch with app.meetings.token.issuer",
        mismatches);
    compareIfConfigured(
        ConfigCompatibilityMismatchCode.AUDIENCE_MISMATCH,
        configSet.audience(),
        meetingsAudience,
        "audience mismatch with app.meetings.token.audience",
        mismatches);
    compareIfConfigured(
        ConfigCompatibilityMismatchCode.ROLE_CLAIM_MISMATCH,
        configSet.roleClaim(),
        meetingsRoleClaim,
        "roleClaim mismatch with app.meetings.token.role-claim-name",
        mismatches);

    compareIfConfigured(
        ConfigCompatibilityMismatchCode.ISSUER_MISMATCH,
        configSet.issuer(),
        contourIssuer,
        "issuer mismatch with app.security.jwt-contour.issuer",
        mismatches);
    compareIfConfigured(
        ConfigCompatibilityMismatchCode.AUDIENCE_MISMATCH,
        configSet.audience(),
        contourAudience,
        "audience mismatch with app.security.jwt-contour.audience",
        mismatches);
    compareIfConfigured(
        ConfigCompatibilityMismatchCode.ROLE_CLAIM_MISMATCH,
        configSet.roleClaim(),
        contourRoleClaim,
        "roleClaim mismatch with app.security.jwt-contour.role-claim",
        mismatches);

    compareIfConfigured(
        ConfigCompatibilityMismatchCode.ENDPOINT_MISMATCH,
        configSet.meetingsServiceUrl(),
        expectedMeetingsServiceUrl,
        "meetingsServiceUrl mismatch with app.meetings.service-url",
        mismatches);
    compareApiVersionIfConfigured(configSet.meetingsServiceUrl(), expectedMeetingsApiVersion, mismatches);

    return new ConfigCompatibilityCheckResult(mismatches.isEmpty(), mismatches, Instant.now(), resolveTraceId(traceId));
  }

  private void requireNonBlank(String value, String fieldName, List<ConfigCompatibilityMismatch> mismatches) {
    if (isBlank(value)) {
      mismatches.add(new ConfigCompatibilityMismatch(
          ConfigCompatibilityMismatchCode.ENDPOINT_MISMATCH,
          fieldName + " is required",
          "non-blank",
          "blank"));
    }
  }

  private void compareIfConfigured(
      ConfigCompatibilityMismatchCode code,
      String current,
      String configured,
      String message,
      List<ConfigCompatibilityMismatch> mismatches) {
    if (isBlank(configured)) {
      return;
    }
    if (!normalizeValue(current).equals(normalizeValue(configured))) {
      mismatches.add(new ConfigCompatibilityMismatch(code, message, configured, current));
    }
  }

  private void compareApiVersionIfConfigured(
      String meetingsServiceUrl,
      String configuredApiVersion,
      List<ConfigCompatibilityMismatch> mismatches) {
    if (isBlank(configuredApiVersion) || isBlank(meetingsServiceUrl)) {
      return;
    }
    String expected = normalizeApiVersion(configuredApiVersion);
    String actual = extractApiVersion(meetingsServiceUrl);
    if (actual.isBlank()) {
      mismatches.add(new ConfigCompatibilityMismatch(
          ConfigCompatibilityMismatchCode.API_VERSION_MISMATCH,
          "No API version found in meetings service URL (expected format: /v1, /v2, etc.)",
          expected,
          "not found"));
    } else if (!expected.equals(actual)) {
      mismatches.add(new ConfigCompatibilityMismatch(
          ConfigCompatibilityMismatchCode.API_VERSION_MISMATCH,
          "meetings API version mismatch",
          expected,
          actual));
    }
  }

  private String extractApiVersion(String meetingsServiceUrl) {
    try {
      URI uri = URI.create(meetingsServiceUrl.trim());
      String path = uri.getPath();
      if (path == null || path.isBlank()) {
        return "";
      }
      String[] parts = path.split("/");
      for (String part : parts) {
        if (part.matches("v\\d+")) {
          return part.toLowerCase(Locale.ROOT);
        }
      }
      return "";
    } catch (IllegalArgumentException ex) {
      return "";
    }
  }

  private String normalizeApiVersion(String value) {
    return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
  }

  private String resolveTraceId(String traceId) {
    if (traceId == null || traceId.isBlank()) {
      return UUID.randomUUID().toString();
    }
    return traceId;
  }

  private String normalizeAlgorithm(String value) {
    return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
  }

  private String normalizeValue(String value) {
    return value == null ? "" : value.trim();
  }

  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}