package com.acme.jitsi.security;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class ProblemDetailsFactory {

  private static final String TRACE_ID_ATTRIBUTE = ProblemDetailsFactory.class.getName() + ".traceId";
  private static final Pattern TRACE_ID_PATTERN = Pattern.compile("^[A-Za-z0-9._:-]{1,128}$");

  public Map<String, Object> build(
      HttpServletRequest request,
      HttpStatus status,
      String title,
      String detail,
      String errorCode) {
    String traceId = resolveTraceId(request);
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
            "traceId", traceId));
  }

  public String resolveTraceId(HttpServletRequest request) {
    String headerTraceId = request.getHeader("X-Trace-Id");
    String normalizedHeaderTraceId = normalizeTraceId(headerTraceId);
    if (normalizedHeaderTraceId != null) {
      request.setAttribute(TRACE_ID_ATTRIBUTE, normalizedHeaderTraceId);
      return normalizedHeaderTraceId;
    }
    Object cachedTraceId = request.getAttribute(TRACE_ID_ATTRIBUTE);
    if (cachedTraceId instanceof String cached && hasText(cached)) {
      return cached;
    }
    String generatedTraceId = createGeneratedTraceId();
    request.setAttribute(TRACE_ID_ATTRIBUTE, generatedTraceId);
    return generatedTraceId;
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

  private String createGeneratedTraceId() {
    long nowMillis = System.currentTimeMillis();
    long nowNanos = System.nanoTime();
    return Long.toHexString(nowMillis) + "-" + Long.toHexString(nowNanos);
  }
}