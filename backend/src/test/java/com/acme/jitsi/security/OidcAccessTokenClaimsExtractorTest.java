package com.acme.jitsi.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class OidcAccessTokenClaimsExtractorTest {

  private final OidcAccessTokenClaimsExtractor extractor = new OidcAccessTokenClaimsExtractor();

  @Test
  void extractsClaimsFromSignedJwtPayloadWithoutCrashingOnSignatureIrrelevance() {
    assertThat(extractor.extract("eyJhbGciOiJIUzI1NiJ9.eyJ0ZW5hbnRJZCI6InRlbmFudC0xIiwicm9sZXMiOlsiYWRtaW4iXX0.c2lnbmF0dXJl"))
        .containsEntry("tenantId", "tenant-1")
        .containsEntry("roles", List.of("admin"));
  }

  @Test
  void returnsEmptyClaimsWhenTokenCannotBeParsed() {
    assertThat(extractor.extract("not-a-jwt")).isEmpty();
  }
}