package com.acme.jitsi.security;

import java.util.Map;
import java.util.Set;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.stereotype.Component;

@Component
public class DefaultJwtAlgorithmPolicy implements JwtAlgorithmPolicy {

  private static final Map<String, MacAlgorithm> SECRET_MAC_ALGORITHMS = Map.of(
      "HS256", MacAlgorithm.HS256,
      "HS384", MacAlgorithm.HS384,
      "HS512", MacAlgorithm.HS512);

  private static final Map<String, String> SECRET_JCA_ALGORITHMS = Map.of(
      "HS256", "HmacSHA256",
      "HS384", "HmacSHA384",
      "HS512", "HmacSHA512");

    private static final Set<String> SECRET_SUPPORTED_ALGORITHMS = Set.copyOf(SECRET_MAC_ALGORITHMS.keySet());

  private static final Map<JwtKeySource, Set<String>> SUPPORTED_BY_KEY_SOURCE = Map.of(
      JwtKeySource.SECRET, SECRET_SUPPORTED_ALGORITHMS,
      JwtKeySource.JWKS, Set.of());

  @Override
  public MacAlgorithm resolveMacAlgorithmForSecret(String configuredAlgorithm) {
    String canonical = canonicalAlgorithm(configuredAlgorithm);
    MacAlgorithm macAlgorithm = SECRET_MAC_ALGORITHMS.get(canonical);
    if (macAlgorithm == null) {
      throw new IllegalArgumentException("Unsupported JWT MAC algorithm: " + configuredAlgorithm);
    }
    return macAlgorithm;
  }

  @Override
  public String resolveJcaAlgorithmForSecret(String configuredAlgorithm) {
    String canonical = canonicalAlgorithm(configuredAlgorithm);
    String jcaAlgorithm = SECRET_JCA_ALGORITHMS.get(canonical);
    if (jcaAlgorithm == null) {
      throw new IllegalArgumentException("Unsupported JWT MAC algorithm: " + configuredAlgorithm);
    }
    return jcaAlgorithm;
  }

  @Override
  public boolean isSupportedForKeySource(String configuredAlgorithm, JwtKeySource keySource) {
    String canonical = canonicalAlgorithm(configuredAlgorithm);
    if (keySource == JwtKeySource.SECRET) {
      return "HS256".equals(canonical)
          || "HS384".equals(canonical)
          || "HS512".equals(canonical);
    }
    return false;
  }

  @Override
  public Set<String> supportedAlgorithmsForKeySource(JwtKeySource keySource) {
    return SUPPORTED_BY_KEY_SOURCE.getOrDefault(keySource, Set.of());
  }

  private String canonicalAlgorithm(String configuredAlgorithm) {
    if (configuredAlgorithm == null) {
      return "";
    }

    String normalized = configuredAlgorithm.trim();

    if ("HS256".equalsIgnoreCase(normalized)) {
      return "HS256";
    }
    if ("HS384".equalsIgnoreCase(normalized)) {
      return "HS384";
    }
    if ("HS512".equalsIgnoreCase(normalized)) {
      return "HS512";
    }
    return normalized;
  }
}