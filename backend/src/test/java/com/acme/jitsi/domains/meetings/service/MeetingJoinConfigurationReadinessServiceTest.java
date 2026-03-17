package com.acme.jitsi.domains.meetings.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MeetingJoinConfigurationReadinessServiceTest {

  @Test
  void inspectReturnsPublicJoinUrlAndOkChecksWhenConfigIsValid() {
    MeetingTokenProperties properties = new MeetingTokenProperties();
    properties.setIssuer("https://portal.example.test");
    properties.setAudience("jitsi-meet");
    properties.setAlgorithm("HS256");
    properties.setSigningSecret("01234567890123456789012345678901");
    properties.setJoinUrlTemplate("https://localhost:8443/%s#jwt=%s");

    MeetingJoinConfigurationReadinessService service = new MeetingJoinConfigurationReadinessService(properties);

    MeetingJoinConfigurationReadiness readiness = service.inspect();

    assertThat(readiness.publicJoinUrl()).isEqualTo("https://localhost:8443/");
    assertThat(readiness.checks()).hasSize(2);
    assertThat(readiness.checks()).allMatch(check -> "ok".equals(check.status()));
  }

  @Test
  void inspectReturnsBlockingErrorWhenSecretIsMissing() {
    MeetingTokenProperties properties = new MeetingTokenProperties();
    properties.setIssuer("https://portal.example.test");
    properties.setAudience("jitsi-meet");
    properties.setAlgorithm("HS256");
    properties.setJoinUrlTemplate("https://localhost:8443/%s#jwt=%s");

    MeetingJoinConfigurationReadinessService service = new MeetingJoinConfigurationReadinessService(properties);

    MeetingJoinConfigurationReadiness readiness = service.inspect();

    assertThat(readiness.checks())
        .anySatisfy(check -> {
          assertThat(check.key()).isEqualTo("token-config");
          assertThat(check.status()).isEqualTo("error");
          assertThat(check.errorCode()).isEqualTo("TOKEN_CONFIG_INVALID");
          assertThat(check.blocking()).isTrue();
        });
  }

  @Test
  void inspectReturnsWarningWhenJoinUrlUsesNonHttpsScheme() {
    MeetingTokenProperties properties = new MeetingTokenProperties();
    properties.setIssuer("https://portal.example.test");
    properties.setAudience("jitsi-meet");
    properties.setAlgorithm("HS256");
    properties.setSigningSecret("01234567890123456789012345678901");
    properties.setJoinUrlTemplate("http://meet.example.test/%s#jwt=%s");

    MeetingJoinConfigurationReadinessService service = new MeetingJoinConfigurationReadinessService(properties);

    MeetingJoinConfigurationReadiness readiness = service.inspect();

    assertThat(readiness.publicJoinUrl()).isEqualTo("http://meet.example.test/");
    assertThat(readiness.checks())
        .anySatisfy(check -> {
          assertThat(check.key()).isEqualTo("join-url");
          assertThat(check.status()).isEqualTo("warn");
          assertThat(check.errorCode()).isEqualTo("JOIN_URL_NOT_HTTPS");
          assertThat(check.blocking()).isFalse();
        });
  }
}