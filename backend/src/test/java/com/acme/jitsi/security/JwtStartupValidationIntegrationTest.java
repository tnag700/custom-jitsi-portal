package com.acme.jitsi.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.acme.jitsi.Application;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ConfigurableApplicationContext;

@ExtendWith(OutputCaptureExtension.class)
class JwtStartupValidationIntegrationTest {

  @AfterEach
  void cleanupPortProperty() {
    System.clearProperty("server.port");
  }

  @Test
  void blocksStartupWhenJwtAlgorithmIsUnsupported() {
    String[] args = new String[] {
      "--app.meetings.token.issuer=https://portal.example.test",
      "--app.meetings.token.audience=jitsi-meet",
      "--app.meetings.token.role-claim-name=role",
      "--app.meetings.token.algorithm=HS256",
      "--app.meetings.token.ttl-minutes=20",
      "--app.meetings.token.signing-secret=01234567890123456789012345678901",
      "--app.auth.refresh.idle-ttl-minutes=60",
      "--app.security.jwt-contour.issuer=https://portal.example.test",
      "--app.security.jwt-contour.audience=jitsi-meet",
      "--app.security.jwt-contour.role-claim=role",
      "--app.security.jwt-contour.algorithm=RS256",
      "--app.security.jwt-contour.access-ttl-minutes=20",
      "--app.security.jwt-contour.refresh-ttl-minutes=60"
    };

    assertThatThrownBy(() -> new SpringApplicationBuilder(Application.class)
        .web(WebApplicationType.SERVLET)
        .properties(baseInfraProperties())
        .run(args))
        .hasRootCauseInstanceOf(JwtStartupValidationException.class)
        .rootCause()
        .satisfies(rootCause -> {
          JwtStartupValidationException exception = (JwtStartupValidationException) rootCause;
          assertThat(exception.errorCode()).isEqualTo(JwtStartupValidationErrorCode.CONFIG_INCOMPATIBLE.name());
          assertThat(exception.getMessage()).contains("Unsupported JWT algorithm");
        });
  }

  @Test
  void startsAndLogsValidationSuccessEvent(CapturedOutput output) throws Exception {
    String[] args = baseJwtArgs();

    try (ConfigurableApplicationContext context = new SpringApplicationBuilder(Application.class)
        .web(WebApplicationType.SERVLET)
        .properties(baseInfraProperties())
        .run(args)) {
      assertThat(context.isActive()).isTrue();
      Object healthEndpoint = context.getBean("healthEndpoint");
      Object health = healthEndpoint.getClass().getMethod("health").invoke(healthEndpoint);
      Object status = health.getClass().getMethod("getStatus").invoke(health);
      String statusCode = (String) status.getClass().getMethod("getCode").invoke(status);
      assertThat(statusCode).isEqualTo("UP");
      assertThat(output.getOut()).contains("CONFIG_VALIDATION_PASSED");
    }
  }

  private String[] baseInfraProperties() {
    return new String[] {
      "spring.datasource.url=jdbc:h2:mem:testdb;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
      "spring.datasource.driver-class-name=org.h2.Driver",
      "spring.jpa.hibernate.ddl-auto=validate",
      "spring.flyway.enabled=true",
      "server.port=0",
      "management.health.redis.enabled=false"
    };
  }

  private String[] baseJwtArgs() {
    return new String[] {
      "--app.meetings.token.issuer=https://portal.example.test",
      "--app.meetings.token.audience=jitsi-meet",
      "--app.meetings.token.role-claim-name=role",
      "--app.meetings.token.algorithm=HS256",
      "--app.meetings.token.ttl-minutes=20",
      "--app.meetings.token.signing-secret=01234567890123456789012345678901",
      "--app.auth.refresh.idle-ttl-minutes=60",
      "--app.security.jwt-contour.issuer=https://portal.example.test",
      "--app.security.jwt-contour.audience=jitsi-meet",
      "--app.security.jwt-contour.role-claim=role",
      "--app.security.jwt-contour.algorithm=HS256",
      "--app.security.jwt-contour.access-ttl-minutes=20",
      "--app.security.jwt-contour.refresh-ttl-minutes=60"
    };
  }
}