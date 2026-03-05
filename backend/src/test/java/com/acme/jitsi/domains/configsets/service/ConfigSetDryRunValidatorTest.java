package com.acme.jitsi.domains.configsets.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.acme.jitsi.security.DefaultJwtAlgorithmPolicy;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ConfigSetDryRunValidatorTest {

  private ConfigSetDryRunValidator validator;

  @BeforeEach
  void setUp() {
    validator = new ConfigSetDryRunValidator(
        new DefaultJwtAlgorithmPolicy(),
        "https://portal.example.test",
        "jitsi-meet",
        "role",
        "https://portal.example.test",
        "jitsi-meet",
      "role",
      "https://meet.example.test/v1",
      "v1");
  }

  @Test
  void validateReturnsSuccessWhenConfigMatchesContourAndHasRequiredFields() {
    DryRunResult result = validator.validate(baseConfigSet("HS256", "secret", null));

    assertThat(result.valid()).isTrue();
    assertThat(result.errors()).isEmpty();
  }

  @Test
  void validateReturnsFailureWhenRequiredFieldsMissing() {
    ConfigSet invalid = new ConfigSet(
        "cs-1",
        "Config",
        "tenant-1",
        ConfigSetEnvironmentType.DEV,
        "",
        "",
        "HS256",
        "",
        "",
        null,
        0,
        120,
        "",
        ConfigSetStatus.DRAFT,
        Instant.parse("2026-01-01T00:00:00Z"),
        Instant.parse("2026-01-01T00:00:00Z"));

    DryRunResult result = validator.validate(invalid);

    assertThat(result.valid()).isFalse();
    assertThat(result.errors()).anyMatch(error -> error.contains("issuer is required"));
    assertThat(result.errors()).anyMatch(error -> error.contains("audience is required"));
    assertThat(result.errors()).anyMatch(error -> error.contains("roleClaim is required"));
    assertThat(result.errors()).anyMatch(error -> error.contains("meetingsServiceUrl is required"));
    assertThat(result.errors()).anyMatch(error -> error.contains("signingSecret is required for HS* algorithms"));
    assertThat(result.errors()).anyMatch(error -> error.contains("accessTtlMinutes must be > 0"));
  }

  @Test
  void validateReturnsFailureForNullConfigSet() {
    DryRunResult result = validator.validate(null);

    assertThat(result.valid()).isFalse();
    assertThat(result.errors()).contains("Config set must be provided");
  }

  @Test
  void validateSkipsContourChecksWhenContourValuesEmpty() {
    ConfigSetDryRunValidator noContour = new ConfigSetDryRunValidator(
      new DefaultJwtAlgorithmPolicy(), "", "", "", "", "", "", "", "");

    DryRunResult result = noContour.validate(baseConfigSet("HS256", "secret", null));

    assertThat(result.valid()).isTrue();
    assertThat(result.errors()).isEmpty();
  }

  @Test
  void validateReturnsFailureForUnsupportedAlgorithm() {
    DryRunResult result = validator.validate(baseConfigSet("ES256", "secret", null));

    assertThat(result.valid()).isFalse();
    assertThat(result.errors()).contains("Unsupported algorithm: ES256");
  }

  @Test
  void validateReturnsFailureForHsAlgorithmWithoutSecret() {
    DryRunResult result = validator.validate(baseConfigSet("HS256", null, null));

    assertThat(result.valid()).isFalse();
    assertThat(result.errors()).contains("signingSecret is required for HS* algorithms");
  }

  @Test
  void validatePerformsCaseSensitiveIssuerComparison() {
    ConfigSet csLower = new ConfigSet(
        "cs-1", "Config", "tenant-1", ConfigSetEnvironmentType.DEV,
        "https://Portal.Example.Test", "jitsi-meet", "HS256", "role",
        "secret", null, 20, 120, "https://meet.example.test",
        ConfigSetStatus.DRAFT,
        Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-01T00:00:00Z"));

    DryRunResult result = validator.validate(csLower);

    assertThat(result.valid()).isFalse();
    assertThat(result.errors()).anyMatch(error -> error.contains("issuer mismatch"));
  }

  @Test
  void validateReturnsFailureWhenAlgorithmRequiresJwks() {
    DryRunResult result = validator.validate(baseConfigSet("RS256", null, null));

    assertThat(result.valid()).isFalse();
    assertThat(result.errors()).contains("jwksUri is required for RS* algorithms");
  }

  @Test
  void validateReturnsFailureWhenAlgorithmNotSupportedForJwks() {
    DryRunResult result = validator.validate(baseConfigSet("RS256", null, "https://issuer.example.test/jwks"));

    assertThat(result.valid()).isFalse();
    assertThat(result.errors()).contains("algorithm is not supported for jwks-based JWT");
  }

  @Test
  void validateReturnsFailureWhenContourMismatchDetected() {
    ConfigSet invalid = new ConfigSet(
        "cs-1",
        "Config",
        "tenant-1",
        ConfigSetEnvironmentType.DEV,
        "https://another.example.test",
        "jitsi-meet",
        "HS256",
        "role",
        "secret",
        null,
        20,
        120,
        "https://meet.example.test",
        ConfigSetStatus.DRAFT,
        Instant.parse("2026-01-01T00:00:00Z"),
        Instant.parse("2026-01-01T00:00:00Z"));

    DryRunResult result = validator.validate(invalid);

    assertThat(result.valid()).isFalse();
    assertThat(result.errors()).anyMatch(error -> error.contains("issuer mismatch"));
  }

  @Test
  void validateCompatibilityReturnsTypedMismatchCodes() {
    ConfigSet invalid = new ConfigSet(
        "cs-1",
        "Config",
        "tenant-1",
        ConfigSetEnvironmentType.DEV,
        "https://another.example.test",
        "other-audience",
        "HS256",
        "other-role",
        "secret",
        null,
        20,
        120,
        "https://meet.example.test/v2",
        ConfigSetStatus.DRAFT,
        Instant.parse("2026-01-01T00:00:00Z"),
        Instant.parse("2026-01-01T00:00:00Z"));

    ConfigCompatibilityCheckResult result = validator.validateCompatibility(invalid, "trace-compat-1");

    assertThat(result.compatible()).isFalse();
    assertThat(result.traceId()).isEqualTo("trace-compat-1");
    assertThat(result.mismatches())
        .extracting(mismatch -> mismatch.code().name())
        .contains("ISSUER_MISMATCH", "AUDIENCE_MISMATCH", "ROLE_CLAIM_MISMATCH", "API_VERSION_MISMATCH");
  }

  private ConfigSet baseConfigSet(String algorithm, String signingSecret, String jwksUri) {
    return new ConfigSet(
        "cs-1",
        "Config",
        "tenant-1",
        ConfigSetEnvironmentType.DEV,
        "https://portal.example.test",
        "jitsi-meet",
        algorithm,
        "role",
        signingSecret,
        jwksUri,
        20,
        120,
        "https://meet.example.test/v1",
        ConfigSetStatus.DRAFT,
        Instant.parse("2026-01-01T00:00:00Z"),
        Instant.parse("2026-01-01T00:00:00Z"));
  }
}