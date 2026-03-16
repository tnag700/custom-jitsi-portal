package com.acme.jitsi.domains.auth.service;

import com.acme.jitsi.shared.ErrorCode;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.time.Instant;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AuthRefreshSecurityEventListenerTest {

  @Test
  void logsSuspiciousEventsAsWarn() {
    Logger logger = (Logger) LoggerFactory.getLogger(AuthRefreshSecurityEventListener.class);
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);

    try {
      AuthAuditLog authAuditLog = mock(AuthAuditLog.class);
      AuthRefreshSecurityEventListener listener = new AuthRefreshSecurityEventListener(authAuditLog, new SimpleMeterRegistry());
      listener.onAuthRefreshSecurityEvent(new AuthRefreshSecurityEvent(
          "REFRESH_REUSE",
          ErrorCode.REFRESH_REUSE_DETECTED.code(),
          "token-1",
          "u-1",
          "meeting-a",
          "trace-auth-1",
          Instant.now()));

      assertThat(appender.list).isNotEmpty();
      ILoggingEvent event = appender.list.getFirst();
      assertThat(event.getLevel()).isEqualTo(Level.WARN);
      assertThat(event.getFormattedMessage()).contains("auth_audit_event");
      assertThat(event.getFormattedMessage()).contains(ErrorCode.REFRESH_REUSE_DETECTED.code());
      verify(authAuditLog).record("REFRESH_REUSE", "u-1", "u-1", "meeting-a", "token-1", ErrorCode.REFRESH_REUSE_DETECTED.code(), "trace-auth-1", null, null);
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
      AuthAuditLog authAuditLog = mock(AuthAuditLog.class);
      AuthRefreshSecurityEventListener listener = new AuthRefreshSecurityEventListener(authAuditLog, new SimpleMeterRegistry());
      listener.onAuthRefreshSecurityEvent(new AuthRefreshSecurityEvent(
          "TOKEN_REFRESHED",
          null,
          "token-2",
          "u-2",
          "meeting-b",
          "trace-auth-2",
          Instant.now()));

      assertThat(appender.list).isNotEmpty();
      ILoggingEvent event = appender.list.getFirst();
      assertThat(event.getLevel()).isEqualTo(Level.INFO);
      assertThat(event.getFormattedMessage()).contains("auth_audit_event");
      assertThat(event.getFormattedMessage()).contains("TOKEN_REFRESHED");
      verify(authAuditLog).record("TOKEN_REFRESHED", "u-2", "u-2", "meeting-b", "token-2", null, "trace-auth-2", null, null);
    } finally {
      logger.detachAppender(appender);
      appender.stop();
    }
  }
}


