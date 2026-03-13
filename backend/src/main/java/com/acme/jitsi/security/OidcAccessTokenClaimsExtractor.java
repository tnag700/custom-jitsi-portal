package com.acme.jitsi.security;

import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.text.ParseException;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
final class OidcAccessTokenClaimsExtractor {

  private static final Logger log = LoggerFactory.getLogger(OidcAccessTokenClaimsExtractor.class);

  Map<String, Object> extract(String tokenValue) {
    try {
      SignedJWT signedJwt = SignedJWT.parse(tokenValue);
      JWTClaimsSet claimsSet = signedJwt.getJWTClaimsSet();
      if (claimsSet == null) {
        return Map.of();
      }
      Map<String, Object> claims = claimsSet.getClaims();
      return claims == null ? Map.of() : claims;
    } catch (ParseException ex) {
      if (log.isDebugEnabled()) {
        log.debug("oidc_access_token_parse_failed message={}", ex.getMessage());
      }
      return Map.of();
    }
  }
}