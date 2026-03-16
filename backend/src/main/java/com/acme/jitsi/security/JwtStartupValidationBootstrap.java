package com.acme.jitsi.security;

import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
  prefix = "app.security.jwt-startup-validation",
  name = "enabled",
  havingValue = "true",
  matchIfMissing = true)
class JwtStartupValidationBootstrap implements InitializingBean {

  private final JwtStartupValidationPolicy validationPolicy;
  private final JwtStartupValidationReporter reporter;

  JwtStartupValidationBootstrap(
      JwtStartupValidationPolicy validationPolicy,
      JwtStartupValidationReporter reporter) {
    this.validationPolicy = validationPolicy;
    this.reporter = reporter;
  }

  @Override
  public void afterPropertiesSet() {
    UUID correlationUuid = UUID.randomUUID();
    String correlationId = correlationUuid.toString();
    try {
      validationPolicy.validateOrThrow();
      reporter.report(new JwtStartupValidationEvent(
          JwtStartupValidationEventType.CONFIG_VALIDATION_PASSED,
          JwtStartupValidationErrorCode.NONE,
          "JWT startup configuration contour is valid.",
          Instant.now(),
          correlationId));
    } catch (JwtStartupValidationException ex) {
      reporter.report(new JwtStartupValidationEvent(
          JwtStartupValidationEventType.CONFIG_VALIDATION_FAILED,
          ex.errorCodeEnum(),
          ex.getMessage(),
          Instant.now(),
          correlationId));
      throw ex;
    }
  }
}