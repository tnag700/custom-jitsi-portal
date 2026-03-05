package com.acme.jitsi.security;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;

@Component
public class ProblemResponseFacade {

  private final ProblemDetailsFactory problemDetailsFactory;

  public ProblemResponseFacade(ProblemDetailsFactory problemDetailsFactory) {
    this.problemDetailsFactory = problemDetailsFactory;
  }

  public Map<String, Object> buildProblemPayload(
      HttpServletRequest request,
      HttpStatus status,
      String title,
      String detail,
      String errorCode) {
    return problemDetailsFactory.build(request, status, title, detail, errorCode);
  }

  public ProblemDetail buildProblemDetail(
      HttpServletRequest request,
      HttpStatus status,
      String title,
      String detail,
      String errorCode) {
    String traceId = resolveTraceId(request);
    String requestUri = request.getRequestURI();
    ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, detail);
    URI instance = URI.create(requestUri);
    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put("title", title);
    properties.put("instance", instance);
    properties.put("errorCode", errorCode);
    properties.put("traceId", traceId);
    applyProblemProperties(problemDetail, properties);
    return problemDetail;
  }

  private void applyProblemProperties(ProblemDetail problemDetail, Map<String, Object> properties) {
    Object title = properties.get("title");
    if (title instanceof String titleValue) {
      problemDetail.setTitle(titleValue);
    }
    Object instance = properties.get("instance");
    if (instance instanceof URI instanceValue) {
      problemDetail.setInstance(instanceValue);
    }
    problemDetail.setProperty("errorCode", properties.get("errorCode"));
    problemDetail.setProperty("traceId", properties.get("traceId"));
  }

  public String resolveTraceId(HttpServletRequest request) {
    return problemDetailsFactory.resolveTraceId(request);
  }
}