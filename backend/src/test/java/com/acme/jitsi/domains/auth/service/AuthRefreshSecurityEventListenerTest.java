package com.acme.jitsi.domains.auth.service;

import com.acme.jitsi.shared.ErrorCode;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class AuthRefreshSecurityEventListenerTest {

  @Test
  void logsSuspiciousEventsAsWarn() {
    Logger logger = (Logger) LoggerFactory.getLogger(AuthRefreshSecurityEventListener.class);
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);

    try {
      AuthRefreshSecurityEventListener listener = new AuthRefreshSecurityEventListener();
      listener.onAuthRefreshSecurityEvent(new AuthRefreshSecurityEvent(
          "REFRESH_REUSE",
          ErrorCode.REFRESH_REUSE_DETECTED.code(),
          "token-1",
          "u-1",
          "meeting-a",
          Instant.now()));

      assertThat(appender.list).isNotEmpty();
      ILoggingEvent event = appender.list.getFirst();
      assertThat(event.getLevel()).isEqualTo(Level.WARN);
      assertThat(event.getFormattedMessage()).contains("auth_refresh_security_event");
      assertThat(event.getFormattedMessage()).contains(ErrorCode.REFRESH_REUSE_DETECTED.code());
    } finally {
      logger.detachAppender(appender);
      appender.stop();
    }
  }

  @Test
  void logsNonSuspiciousEventsAsInfo() {
    Logger logger = (Logger) LoggerFactory.getLogger(AuthRefreshSecurityEventListener.class);
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);

    try {
      AuthRefreshSecurityEventListener listener = new AuthRefreshSecurityEventListener();
      listener.onAuthRefreshSecurityEvent(new AuthRefreshSecurityEvent(
          "REFRESH_EXPIRED",
          ErrorCode.AUTH_REQUIRED.code(),
          "token-2",
          "u-2",
          "meeting-b",
          Instant.now()));

      assertThat(appender.list).isNotEmpty();
      ILoggingEvent event = appender.list.getFirst();
      assertThat(event.getLevel()).isEqualTo(Level.INFO);
      assertThat(event.getFormattedMessage()).contains("auth_refresh_security_event");
      assertThat(event.getFormattedMessage()).contains(ErrorCode.AUTH_REQUIRED.code());
    } finally {
      logger.detachAppender(appender);
      appender.stop();
    }
  }
}


