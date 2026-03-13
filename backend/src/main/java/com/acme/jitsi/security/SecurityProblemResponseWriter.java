package com.acme.jitsi.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
final class SecurityProblemResponseWriter {

  private static final Logger log = LoggerFactory.getLogger(SecurityProblemResponseWriter.class);
  private static final String UTF_8_CHARSET = StandardCharsets.UTF_8.name();

  private final JsonMapper jsonMapper;
  private final ProblemResponseFacade problemResponseFacade;

  SecurityProblemResponseWriter(JsonMapper jsonMapper, ProblemResponseFacade problemResponseFacade) {
    this.jsonMapper = jsonMapper;
    this.problemResponseFacade = problemResponseFacade;
  }

  void writeProblem(
      HttpServletRequest request,
      HttpServletResponse response,
      ProblemDetailsMappingPolicy.ProblemDefinition definition) throws IOException {
    writeProblem(
        request,
        response,
        definition.status(),
        definition.title(),
        definition.detail(),
        definition.errorCode());
  }

  void writeProblem(
      HttpServletRequest request,
      HttpServletResponse response,
      HttpStatus status,
      String title,
      String detail,
      String errorCode) throws IOException {
    String traceId = resolveTraceId(request);
    String requestId = problemResponseFacade.resolveRequestId(request);
    if (log.isInfoEnabled()) {
      log.info(
          "problem_response status={} code={} path={} traceId={} requestId={}",
          status.value(),
          errorCode,
          request.getRequestURI(),
          traceId,
          requestId);
    }

    Map<String, Object> payload = problemResponseFacade.buildProblemPayload(request, status, title, detail, errorCode);
    response.setStatus(status.value());
    response.setCharacterEncoding(UTF_8_CHARSET);
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    response.getWriter().write(jsonMapper.writeValueAsString(payload));
  }

  String resolveTraceId(HttpServletRequest request) {
    return problemResponseFacade.resolveTraceId(request);
  }
}