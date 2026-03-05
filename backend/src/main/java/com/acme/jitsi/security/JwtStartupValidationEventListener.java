package com.acme.jitsi.security;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Counter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
class JwtStartupValidationEventListener implements JwtStartupValidationReporter {

  private static final Logger log = LoggerFactory.getLogger(JwtStartupValidationEventListener.class);

  private final MeterRegistry meterRegistry;

  JwtStartupValidationEventListener(MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
  }

  @Override
  public void report(JwtStartupValidationEvent event) {
    JwtStartupValidationEventType eventType = event.eventType();
    JwtStartupValidationErrorCode errorCode = event.errorCode();
    String message = event.message();
    java.time.Instant occurredAt = event.occurredAt();
    String correlationId = event.correlationId();
    String eventTypeName = mapEventTypeName(eventType);
    String errorCodeName = mapErrorCodeName(errorCode);

    if (eventType == JwtStartupValidationEventType.CONFIG_VALIDATION_FAILED) {
      if (log.isErrorEnabled()) {
        log.error(
            "startup_security_config_event eventType={} errorCode={} message={} occurredAt={} correlationId={}",
            eventType,
            errorCode,
            message,
            occurredAt,
            correlationId);
      }
    } else {
      if (log.isInfoEnabled()) {
        log.info(
            "startup_security_config_event eventType={} errorCode={} message={} occurredAt={} correlationId={}",
            eventType,
            errorCode,
            message,
            occurredAt,
            correlationId);
      }
    }

    Counter startupEventsCounter = meterRegistry.counter(
        "startup.config.validation.events",
        "eventType",
      eventTypeName,
        "errorCode",
      errorCodeName);
    startupEventsCounter.increment();

    if (eventType == JwtStartupValidationEventType.CONFIG_VALIDATION_FAILED) {
      Counter startupErrorsCounter = meterRegistry.counter(
          "startup.config.validation.errors",
          "errorCode",
          errorCodeName);
      startupErrorsCounter.increment();
    }
  }

  private String mapEventTypeName(JwtStartupValidationEventType eventType) {
    return switch (eventType) {
      case CONFIG_VALIDATION_PASSED -> "CONFIG_VALIDATION_PASSED";
      case CONFIG_VALIDATION_FAILED -> "CONFIG_VALIDATION_FAILED";
    };
  }

  private String mapErrorCodeName(JwtStartupValidationErrorCode errorCode) {
    return switch (errorCode) {
      case NONE -> "NONE";
      case CONFIG_MISSING_REQUIRED -> "CONFIG_MISSING_REQUIRED";
      case CONFIG_INCOMPATIBLE -> "CONFIG_INCOMPATIBLE";
      case JWT_CONFIG_MISMATCH -> "JWT_CONFIG_MISMATCH";
    };
  }
}