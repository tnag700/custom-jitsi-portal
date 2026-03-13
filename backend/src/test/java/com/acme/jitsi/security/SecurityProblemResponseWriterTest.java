package com.acme.jitsi.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.Map;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class SecurityProblemResponseWriterTest {

  private final JsonMapper jsonMapper = JsonMapper.builder().build();
  private final ProblemResponseFacade problemResponseFacade = mock(ProblemResponseFacade.class);
  private final SecurityProblemResponseWriter writer = new SecurityProblemResponseWriter(jsonMapper, problemResponseFacade);

  @Test
  void writesProblemPayloadWithProblemJsonContentTypeAndUtf8() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/rooms");
    MockHttpServletResponse response = new MockHttpServletResponse();
    when(problemResponseFacade.resolveTraceId(any(HttpServletRequest.class))).thenReturn("trace-writer-1");
    when(problemResponseFacade.buildProblemPayload(
        any(HttpServletRequest.class),
        eq(HttpStatus.FORBIDDEN),
        eq("Access denied"),
        eq("Forbidden"),
        eq("ACCESS_DENIED")))
        .thenReturn(Map.of(
            "type", "about:blank",
            "title", "Access denied",
            "status", 403,
            "detail", "Forbidden",
            "instance", "/api/v1/rooms",
          "properties", Map.of(
            "errorCode", "ACCESS_DENIED",
            "traceId", "trace-writer-1",
            "requestId", "trace-writer-1")));

    writer.writeProblem(request, response, HttpStatus.FORBIDDEN, "Access denied", "Forbidden", "ACCESS_DENIED");

    assertThat(response.getStatus()).isEqualTo(403);
    assertThat(response.getCharacterEncoding()).isEqualTo("UTF-8");
    assertThat(response.getContentType()).startsWith(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    assertThat(readBody(response.getContentAsString()))
        .containsEntry("instance", "/api/v1/rooms")
        .containsEntry("status", 403);
    verify(problemResponseFacade).buildProblemPayload(
        any(HttpServletRequest.class),
        eq(HttpStatus.FORBIDDEN),
        eq("Access denied"),
        eq("Forbidden"),
        eq("ACCESS_DENIED"));
  }

  @Test
  void delegatesTraceIdResolutionToProblemResponseFacade() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    when(problemResponseFacade.resolveTraceId(request)).thenReturn("trace-writer-2");

    assertThat(writer.resolveTraceId(request)).isEqualTo("trace-writer-2");
  }

  @Test
  void logsStableExplicitTraceIdFieldWhenWritingProblemPayload() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/rooms");
    MockHttpServletResponse response = new MockHttpServletResponse();
    when(problemResponseFacade.resolveTraceId(any(HttpServletRequest.class))).thenReturn("trace-writer-log-1");
    when(problemResponseFacade.resolveRequestId(any(HttpServletRequest.class))).thenReturn("request-writer-log-1");
    when(problemResponseFacade.buildProblemPayload(
        any(HttpServletRequest.class),
        eq(HttpStatus.FORBIDDEN),
        eq("Access denied"),
        eq("Forbidden"),
        eq("ACCESS_DENIED")))
        .thenReturn(Map.of(
            "type", "about:blank",
            "title", "Access denied",
            "status", 403,
            "detail", "Forbidden",
            "instance", "/api/v1/rooms",
          "properties", Map.of(
            "errorCode", "ACCESS_DENIED",
            "traceId", "trace-writer-log-1",
            "requestId", "request-writer-log-1")));

    Logger logger = (Logger) LoggerFactory.getLogger(SecurityProblemResponseWriter.class);
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
    try {
      writer.writeProblem(request, response, HttpStatus.FORBIDDEN, "Access denied", "Forbidden", "ACCESS_DENIED");
    } finally {
      logger.detachAppender(appender);
    }

    assertThat(appender.list)
        .extracting(ILoggingEvent::getFormattedMessage)
        .anySatisfy(message -> assertThat(message)
            .contains("problem_response status=403")
            .contains("code=ACCESS_DENIED")
            .contains("path=/api/v1/rooms")
          .contains("traceId=trace-writer-log-1")
          .contains("requestId=request-writer-log-1"));
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> readBody(String content) throws Exception {
    ObjectMapper mapper = jsonMapper;
    return mapper.readValue(content, Map.class);
  }
}