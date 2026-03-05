package com.acme.jitsi.domains.configsets.usecase;

import com.acme.jitsi.domains.configsets.service.ConfigSetEnvironmentType;
import com.acme.jitsi.domains.configsets.service.ConfigSetInvalidDataException;
import com.acme.jitsi.security.JwtAlgorithmPolicy;
import com.acme.jitsi.security.JwtKeySource;
import java.net.URI;
import java.util.Locale;

final class ConfigSetCommandSupport {

  private final JwtAlgorithmPolicy jwtAlgorithmPolicy;

  ConfigSetCommandSupport(JwtAlgorithmPolicy jwtAlgorithmPolicy) {
    this.jwtAlgorithmPolicy = jwtAlgorithmPolicy;
  }

  void validate(
      ConfigSetEnvironmentType environmentType,
      int accessTtlMinutes,
      String algorithmRaw,
      String signingSecretRaw,
      String jwksUriRaw) {
    validateRequiredInputs(environmentType, accessTtlMinutes);
    String algorithm = normalizeAlgorithm(algorithmRaw);
    boolean hasSecret = normalizeOptional(signingSecretRaw) != null;
    boolean hasJwks = normalizeOptional(jwksUriRaw) != null;

    validateSourcePresence(algorithm, hasSecret, hasJwks);
    validateSupportedAlgorithms(algorithm, hasSecret, hasJwks);
  }

  String normalizeAlgorithm(String value) {
    String normalized = normalizeRequired(value, "Algorithm is required").toUpperCase(Locale.ROOT);
    if (!(isHsAlgorithm(normalized) || isRsAlgorithm(normalized))) {
      throw new ConfigSetInvalidDataException("Unsupported algorithm: " + value);
    }
    return normalized;
  }

  String normalizeRoleClaim(String value) {
    String normalized = normalizeOptional(value);
    return normalized == null ? "role" : normalized;
  }

  String normalizeAndValidateUrl(String value, String message) {
    String normalized = normalizeRequired(value, "Meetings service URL is required");
    try {
      URI uri = URI.create(normalized);
      if (uri.getScheme() == null || uri.getHost() == null) {
        throw new ConfigSetInvalidDataException(message);
      }
      return normalized;
    } catch (IllegalArgumentException ex) {
      throw new ConfigSetInvalidDataException(message, ex);
    }
  }

  private void validateRequiredInputs(ConfigSetEnvironmentType environmentType, int accessTtlMinutes) {
    if (environmentType == null) {
      throw new ConfigSetInvalidDataException("Environment type is required");
    }
    if (accessTtlMinutes <= 0) {
      throw new ConfigSetInvalidDataException("Access TTL must be greater than 0");
    }
  }

  private void validateSourcePresence(String algorithm, boolean hasSecret, boolean hasJwks) {
    if (hasNoCredentialSource(hasSecret, hasJwks)) {
      throw new ConfigSetInvalidDataException("Either signingSecret or jwksUri must be provided");
    }
    if (isSecretAlgorithmWithoutSecret(algorithm, hasSecret)) {
      throw new ConfigSetInvalidDataException("HS algorithms require signingSecret");
    }
    if (isJwksAlgorithmWithoutJwks(algorithm, hasJwks)) {
      throw new ConfigSetInvalidDataException("RS algorithms require jwksUri");
    }
  }

  private void validateSupportedAlgorithms(String algorithm, boolean hasSecret, boolean hasJwks) {
    if (hasSecret) {
      validateSecretAlgorithmSupport(algorithm);
    }
    if (hasJwks) {
      validateJwksAlgorithmSupport(algorithm);
    }
  }

  private boolean hasNoCredentialSource(boolean hasSecret, boolean hasJwks) {
    return !hasSecret && !hasJwks;
  }

  private boolean isSecretAlgorithmWithoutSecret(String algorithm, boolean hasSecret) {
    return isHsAlgorithm(algorithm) && !hasSecret;
  }

  private boolean isJwksAlgorithmWithoutJwks(String algorithm, boolean hasJwks) {
    return isRsAlgorithm(algorithm) && !hasJwks;
  }

  private void validateSecretAlgorithmSupport(String algorithm) {
    if (!jwtAlgorithmPolicy.isSupportedForKeySource(algorithm, JwtKeySource.SECRET)) {
      throw new ConfigSetInvalidDataException("Unsupported algorithm for signingSecret: " + algorithm);
    }
  }

  private void validateJwksAlgorithmSupport(String algorithm) {
    if (!isRsAlgorithm(algorithm)) {
      throw new ConfigSetInvalidDataException("JWKS is supported only for RS algorithms");
    }
    if (!jwtAlgorithmPolicy.isSupportedForKeySource(algorithm, JwtKeySource.JWKS)) {
      throw new ConfigSetInvalidDataException("Unsupported algorithm for jwksUri: " + algorithm);
    }
  }

  String normalizeRequired(String value, String message) {
    if (value == null || value.isBlank()) {
      throw new ConfigSetInvalidDataException(message);
    }
    return value.trim();
  }

  String normalizeOptional(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  private boolean isHsAlgorithm(String algorithm) {
    return algorithm.startsWith("HS");
  }

  private boolean isRsAlgorithm(String algorithm) {
    return algorithm.startsWith("RS");
  }
}
