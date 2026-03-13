package com.acme.jitsi.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.acme.jitsi.shared.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

@Component
final class JsonSecurityAuthenticationEntryPoint implements AuthenticationEntryPoint {

  private static final Logger log = LoggerFactory.getLogger(JsonSecurityAuthenticationEntryPoint.class);

  private final ProblemDetailsMappingPolicy problemDetailsMappingPolicy;
  private final SecurityProblemResponseWriter securityProblemResponseWriter;

  JsonSecurityAuthenticationEntryPoint(
      ProblemDetailsMappingPolicy problemDetailsMappingPolicy,
      SecurityProblemResponseWriter securityProblemResponseWriter) {
    this.problemDetailsMappingPolicy = problemDetailsMappingPolicy;
    this.securityProblemResponseWriter = securityProblemResponseWriter;
  }

  @Override
  public void commence(
      HttpServletRequest request,
      HttpServletResponse response,
      AuthenticationException authException) throws java.io.IOException {
    ProblemDetailsMappingPolicy.ProblemDefinition definition = problemDetailsMappingPolicy.mapSecurityAuthRequired();
    String traceId = securityProblemResponseWriter.resolveTraceId(request);
    if (log.isWarnEnabled()) {
      log.warn(
          "authentication_required code={} path={} traceId={} exceptionType={}",
          ErrorCode.AUTH_REQUIRED.code(),
          request.getRequestURI(),
          traceId,
          authException == null ? "unknown" : authException.getClass().getSimpleName());
    }
    securityProblemResponseWriter.writeProblem(request, response, definition);
  }
}