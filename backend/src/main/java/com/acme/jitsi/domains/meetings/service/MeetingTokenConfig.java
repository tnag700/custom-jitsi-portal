package com.acme.jitsi.domains.meetings.service;

import com.acme.jitsi.security.JwtAlgorithmPolicy;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.proc.SecurityContext;
import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

@Configuration
class MeetingTokenConfig {

  private final JwtAlgorithmPolicy algorithmPolicy;

  MeetingTokenConfig(JwtAlgorithmPolicy algorithmPolicy) {
    this.algorithmPolicy = algorithmPolicy;
  }

  private SecretKey signingKey(MeetingTokenProperties properties) {
    return new SecretKeySpec(
        properties.signingSecret().getBytes(StandardCharsets.UTF_8),
        algorithmPolicy.resolveJcaAlgorithmForSecret(properties.algorithm()));
  }

  @Bean
  JwtEncoder meetingJwtEncoder(MeetingTokenProperties properties) {
    return new NimbusJwtEncoder(new ImmutableSecret<SecurityContext>(signingKey(properties)));
  }

  @Bean
  JwtDecoder meetingJwtDecoder(MeetingTokenProperties properties) {
    return NimbusJwtDecoder.withSecretKey(signingKey(properties))
        .macAlgorithm(algorithmPolicy.resolveMacAlgorithmForSecret(properties.algorithm()))
        .build();
  }
}
