package com.acme.jitsi.security;

import java.util.Set;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;

public interface JwtAlgorithmPolicy {

  MacAlgorithm resolveMacAlgorithmForSecret(String configuredAlgorithm);

  String resolveJcaAlgorithmForSecret(String configuredAlgorithm);

  boolean isSupportedForKeySource(String configuredAlgorithm, JwtKeySource keySource);

  Set<String> supportedAlgorithmsForKeySource(JwtKeySource keySource);
}