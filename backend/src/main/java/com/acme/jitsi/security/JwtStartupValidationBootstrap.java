package com.acme.jitsi.security;

import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

@Component
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