package com.acme.jitsi.security;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;

class MaskedErrorDispatchDiagnosticsLoggerTest {

  private final MaskedErrorDispatchDiagnosticsLogger logger = new MaskedErrorDispatchDiagnosticsLogger();

  @Test
  void logsConcreteExceptionTypesForMaskedErrorDispatch() {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/error");
    request.setAttribute("jakarta.servlet.error.status_code", 500);
    request.setAttribute("jakarta.servlet.error.request_uri", "/api/v1/rooms");
    request.setAttribute("jakarta.servlet.error.exception", new IllegalStateException("boom"));

    Logger targetLogger = (Logger) LoggerFactory.getLogger(MaskedErrorDispatchDiagnosticsLogger.class);
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    targetLogger.addAppender(appender);
    try {
      logger.logIfPresent(request, "trace-logger-1", new AccessDeniedException("denied"));
    } finally {
      targetLogger.detachAppender(appender);
    }

    assertThat(appender.list)
        .hasSize(1)
        .first()
        .extracting(ILoggingEvent::getLevel, ILoggingEvent::getFormattedMessage)
        .containsExactly(
            Level.ERROR,
            "masked_error_dispatch path=/error traceId=trace-logger-1 originalStatus=500 originalPath=/api/v1/rooms originalExceptionType=IllegalStateException originalException=java.lang.IllegalStateException: boom securityExceptionType=AccessDeniedException");
  }

  @Test
  void skipsLoggingForNonErrorDispatchPath() {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/rooms");

    Logger targetLogger = (Logger) LoggerFactory.getLogger(MaskedErrorDispatchDiagnosticsLogger.class);
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    targetLogger.addAppender(appender);
    try {
      logger.logIfPresent(request, "trace-logger-2", new AccessDeniedException("denied"));
    } finally {
      targetLogger.detachAppender(appender);
    }

    assertThat(appender.list).isEmpty();
  }
}