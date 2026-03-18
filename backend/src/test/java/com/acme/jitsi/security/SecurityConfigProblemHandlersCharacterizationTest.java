package com.acme.jitsi.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class SecurityConfigProblemHandlersCharacterizationTest {

  private final JsonMapper jsonMapper = JsonMapper.builder().build();
  private final ProblemResponseFacade problemResponseFacade = mock(ProblemResponseFacade.class);
  private final ProblemDetailsMappingPolicy problemDetailsMappingPolicy = mock(ProblemDetailsMappingPolicy.class);
  private final SecurityProblemResponseWriter securityProblemResponseWriter = new SecurityProblemResponseWriter(
      jsonMapper,
      problemResponseFacade);
  private final JsonSecurityAuthenticationEntryPoint authenticationEntryPoint = new JsonSecurityAuthenticationEntryPoint(
      problemDetailsMappingPolicy,
      securityProblemResponseWriter);
  private final JsonSecurityAccessDeniedHandler accessDeniedHandler = new JsonSecurityAccessDeniedHandler(
      problemDetailsMappingPolicy,
      securityProblemResponseWriter,
      new MaskedErrorDispatchDiagnosticsLogger());

  @Test
  void authenticationEntryPointWritesStableProblemJsonContract() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/rooms");
    MockHttpServletResponse response = new MockHttpServletResponse();
    when(problemDetailsMappingPolicy.mapSecurityAuthRequired())
        .thenReturn(new ProblemDetailsMappingPolicy.ProblemDefinition(
            HttpStatus.UNAUTHORIZED,
            "Authentication required",
            "Authentication is required to access this resource.",
            "AUTH_REQUIRED"));
    when(problemResponseFacade.resolveTraceId(any(HttpServletRequest.class))).thenReturn("trace-auth-1");
    when(problemResponseFacade.buildProblemPayload(
        any(HttpServletRequest.class),
        any(HttpStatus.class),
        any(String.class),
        any(String.class),
        any(String.class)))
        .thenReturn(Map.of(
            "type", "about:blank",
            "title", "Authentication required",
            "status", 401,
            "detail", "Authentication is required to access this resource.",
            "instance", "/api/v1/rooms",
            "properties", Map.of(
                "errorCode", "AUTH_REQUIRED",
                "traceId", "trace-auth-1")));

    authenticationEntryPoint.commence(
        request,
        response,
        new InsufficientAuthenticationException("auth required"));

    assertThat(response.getStatus()).isEqualTo(401);
    assertThat(response.getContentType()).startsWith(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    assertThat(response.getCharacterEncoding()).isEqualTo("UTF-8");

    Map<String, Object> payload = readBody(response.getContentAsString());
    assertThat(payload).containsEntry("title", "Authentication required");
    assertThat(payload).containsEntry("status", 401);
    assertThat(payload).containsEntry("detail", "Authentication is required to access this resource.");
    assertThat(payload).containsEntry("instance", "/api/v1/rooms");
    assertThat(castMap(payload.get("properties")))
        .containsEntry("errorCode", "AUTH_REQUIRED")
        .containsEntry("traceId", "trace-auth-1");
  }

  @Test
  void accessDeniedHandlerKeepsMaskedErrorDispatchDiagnosticsObservable() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/error");
    request.setAttribute("jakarta.servlet.error.status_code", 500);
    request.setAttribute("jakarta.servlet.error.request_uri", "/api/v1/rooms");
    request.setAttribute("jakarta.servlet.error.exception", new IllegalStateException("boom"));
    MockHttpServletResponse response = new MockHttpServletResponse();
    when(problemDetailsMappingPolicy.mapSecurityAccessDenied())
        .thenReturn(new ProblemDetailsMappingPolicy.ProblemDefinition(
            HttpStatus.FORBIDDEN,
            "Access denied",
            "You do not have permission to access this resource.",
            "ACCESS_DENIED"));
    when(problemResponseFacade.resolveTraceId(any(HttpServletRequest.class))).thenReturn("trace-denied-1");
    when(problemResponseFacade.buildProblemPayload(
        any(HttpServletRequest.class),
        any(HttpStatus.class),
        any(String.class),
        any(String.class),
        any(String.class)))
        .thenReturn(Map.of(
            "type", "about:blank",
            "title", "Access denied",
            "status", 403,
            "detail", "You do not have permission to access this resource.",
            "instance", "/error",
            "properties", Map.of(
                "errorCode", "ACCESS_DENIED",
                "traceId", "trace-denied-1")));

        Logger logger = (Logger) LoggerFactory.getLogger(MaskedErrorDispatchDiagnosticsLogger.class);
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
    try {
            accessDeniedHandler.handle(
          request,
          response,
          new AccessDeniedException("denied"));
    } finally {
      logger.detachAppender(appender);
    }

    assertThat(response.getStatus()).isEqualTo(403);
    assertThat(response.getContentType()).startsWith(MediaType.APPLICATION_PROBLEM_JSON_VALUE);

    List<String> formattedMessages = appender.list.stream()
        .map(ILoggingEvent::getFormattedMessage)
        .toList();
    assertThat(formattedMessages)
        .anySatisfy(message -> assertThat(message)
            .contains("masked_error_dispatch")
            .contains("traceId=trace-denied-1")
            .contains("originalStatus=500")
            .contains("originalPath=/api/v1/rooms")
            .contains("originalExceptionType=IllegalStateException")
            .contains("securityExceptionType=AccessDeniedException"));
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> readBody(String content) throws Exception {
    ObjectMapper mapper = jsonMapper;
    return mapper.readValue(content, Map.class);
  }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object value) {
        return (Map<String, Object>) value;
    }
}