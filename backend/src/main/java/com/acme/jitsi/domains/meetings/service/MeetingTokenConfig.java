package com.acme.jitsi.domains.meetings.service;

import com.acme.jitsi.security.JwtAlgorithmPolicy;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.proc.SecurityContext;
import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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

  private static SecretKey signingKey(
      MeetingTokenProperties properties,
      JwtAlgorithmPolicy algorithmPolicy) {
    return new SecretKeySpec(
        properties.signingSecret().getBytes(StandardCharsets.UTF_8),
        algorithmPolicy.resolveJcaAlgorithmForSecret(properties.algorithm()));
  }

  JwtEncoder meetingJwtEncoder(MeetingTokenProperties properties) {
    return meetingJwtEncoderBean(properties, algorithmPolicy);
  }

  JwtDecoder meetingJwtDecoder(MeetingTokenProperties properties) {
    return meetingJwtDecoderBean(properties, algorithmPolicy);
  }

  @Bean("meetingJwtEncoder")
  @ConditionalOnProperty(prefix = "app.meetings.token", name = "signing-secret")
  static JwtEncoder meetingJwtEncoderBean(
      MeetingTokenProperties properties,
      JwtAlgorithmPolicy algorithmPolicy) {
    return new NimbusJwtEncoder(
        new ImmutableSecret<SecurityContext>(signingKey(properties, algorithmPolicy)));
  }

  @Bean("meetingJwtDecoder")
  @ConditionalOnProperty(prefix = "app.meetings.token", name = "signing-secret")
  static JwtDecoder meetingJwtDecoderBean(
      MeetingTokenProperties properties,
      JwtAlgorithmPolicy algorithmPolicy) {
    return NimbusJwtDecoder.withSecretKey(signingKey(properties, algorithmPolicy))
        .macAlgorithm(algorithmPolicy.resolveMacAlgorithmForSecret(properties.algorithm()))
        .build();
  }
}
