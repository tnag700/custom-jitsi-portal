package com.acme.jitsi.security;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class ProblemDetailsFactory {

  private static final String TRACE_ID_ATTRIBUTE = ProblemDetailsFactory.class.getName() + ".traceId";
  private static final String REQUEST_ID_ATTRIBUTE = ProblemDetailsFactory.class.getName() + ".requestId";
  private static final Pattern TRACE_ID_PATTERN = Pattern.compile("^[A-Za-z0-9._:-]{1,128}$");

  public Map<String, Object> build(
      HttpServletRequest request,
      HttpStatus status,
      String title,
      String detail,
      String errorCode) {
    String traceId = resolveTraceId(request);
    String requestId = resolveRequestId(request);
    String instance = request.getRequestURI();
    int statusCode = status.value();
    return Map.of(
        "type", "about:blank",
        "title", title,
      "status", statusCode,
        "detail", detail,
      "instance", instance,
        "properties", Map.of(
            "errorCode", errorCode,
            "traceId", traceId,
            "requestId", requestId));
  }

  public String resolveTraceId(HttpServletRequest request) {
    Object cachedTraceId = request.getAttribute(TRACE_ID_ATTRIBUTE);
    if (cachedTraceId instanceof String cached && hasText(cached)) {
      return cached;
    }

    String currentTraceId = currentTraceId();
    if (currentTraceId != null) {
      request.setAttribute(TRACE_ID_ATTRIBUTE, currentTraceId);
      return currentTraceId;
    }

    String normalizedHeaderTraceId = normalizeTraceId(request.getHeader("X-Trace-Id"));
    if (normalizedHeaderTraceId != null) {
      request.setAttribute(TRACE_ID_ATTRIBUTE, normalizedHeaderTraceId);
      return normalizedHeaderTraceId;
    }

    String generatedTraceId = createGeneratedId();
    request.setAttribute(TRACE_ID_ATTRIBUTE, generatedTraceId);
    return generatedTraceId;
  }

  public String resolveRequestId(HttpServletRequest request) {
    Object cachedRequestId = request.getAttribute(REQUEST_ID_ATTRIBUTE);
    if (cachedRequestId instanceof String cached && hasText(cached)) {
      return cached;
    }

    String normalizedHeaderTraceId = normalizeTraceId(request.getHeader("X-Trace-Id"));
    if (normalizedHeaderTraceId != null) {
      request.setAttribute(REQUEST_ID_ATTRIBUTE, normalizedHeaderTraceId);
      return normalizedHeaderTraceId;
    }

    String generatedRequestId = createGeneratedId();
    request.setAttribute(REQUEST_ID_ATTRIBUTE, generatedRequestId);
    return generatedRequestId;
  }

  private String normalizeTraceId(String traceId) {
    if (traceId == null) {
      return null;
    }
    String trimmed = traceId.trim();
    if (!hasText(trimmed)) {
      return null;
    }
    Matcher traceIdMatcher = TRACE_ID_PATTERN.matcher(trimmed);
    boolean traceIdValid = traceIdMatcher.matches();
    if (!traceIdValid) {
      return null;
    }
    return trimmed;
  }

  private boolean hasText(String value) {
    if (value == null) {
      return false;
    }
    for (int i = 0; i < value.length(); i++) {
      if (!Character.isWhitespace(value.charAt(i))) {
        return true;
      }
    }
    return false;
  }

  private String currentTraceId() {
    SpanContext spanContext = Span.current().getSpanContext();
    if (!spanContext.isValid()) {
      return null;
    }
    return spanContext.getTraceId();
  }

  private String createGeneratedId() {
    return UUID.randomUUID().toString().replace("-", "");
  }
}