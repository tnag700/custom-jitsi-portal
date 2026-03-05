package com.acme.jitsi.security;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class JwtStartupValidationEventListenerTest {

  @Test
  void logsFailedValidationAsErrorAndIncrementsErrorMetric() {
    Logger logger = (Logger) LoggerFactory.getLogger(JwtStartupValidationEventListener.class);
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);

    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    JwtStartupValidationEventListener listener = new JwtStartupValidationEventListener(meterRegistry);

    try {
        listener.report(new JwtStartupValidationEvent(
          JwtStartupValidationEventType.CONFIG_VALIDATION_FAILED,
          JwtStartupValidationErrorCode.CONFIG_INCOMPATIBLE,
          "Unsupported algorithm",
          Instant.now(),
          "corr-1"));

      assertThat(appender.list).isNotEmpty();
      ILoggingEvent event = appender.list.getFirst();
      assertThat(event.getLevel()).isEqualTo(Level.ERROR);
      assertThat(event.getFormattedMessage()).contains("startup_security_config_event");
      assertThat(event.getFormattedMessage()).contains("CONFIG_VALIDATION_FAILED");
      assertThat(event.getFormattedMessage()).contains("correlationId=corr-1");

      assertThat(meterRegistry.get("startup.config.validation.events").counter().count()).isEqualTo(1.0);
      assertThat(meterRegistry.get("startup.config.validation.errors").counter().count()).isEqualTo(1.0);
    } finally {
      logger.detachAppender(appender);
      appender.stop();
      meterRegistry.close();
    }
  }

  @Test
  void logsPassedValidationAsInfo() {
    Logger logger = (Logger) LoggerFactory.getLogger(JwtStartupValidationEventListener.class);
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);

    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    JwtStartupValidationEventListener listener = new JwtStartupValidationEventListener(meterRegistry);

    try {
        listener.report(new JwtStartupValidationEvent(
          JwtStartupValidationEventType.CONFIG_VALIDATION_PASSED,
          JwtStartupValidationErrorCode.NONE,
          "OK",
          Instant.now(),
          "corr-2"));

      assertThat(appender.list).isNotEmpty();
      ILoggingEvent event = appender.list.getFirst();
      assertThat(event.getLevel()).isEqualTo(Level.INFO);
      assertThat(event.getFormattedMessage()).contains("CONFIG_VALIDATION_PASSED");
      assertThat(event.getFormattedMessage()).contains("correlationId=corr-2");
      assertThat(meterRegistry.get("startup.config.validation.events").counter().count()).isEqualTo(1.0);
    } finally {
      logger.detachAppender(appender);
      appender.stop();
      meterRegistry.close();
    }
  }
}