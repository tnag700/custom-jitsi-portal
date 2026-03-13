package com.acme.jitsi.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.acme.jitsi.shared.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

@Component
final class JsonSecurityAccessDeniedHandler implements AccessDeniedHandler {

  private static final Logger log = LoggerFactory.getLogger(JsonSecurityAccessDeniedHandler.class);

  private final ProblemDetailsMappingPolicy problemDetailsMappingPolicy;
  private final SecurityProblemResponseWriter securityProblemResponseWriter;
  private final MaskedErrorDispatchDiagnosticsLogger maskedErrorDispatchDiagnosticsLogger;

  JsonSecurityAccessDeniedHandler(
      ProblemDetailsMappingPolicy problemDetailsMappingPolicy,
      SecurityProblemResponseWriter securityProblemResponseWriter,
      MaskedErrorDispatchDiagnosticsLogger maskedErrorDispatchDiagnosticsLogger) {
    this.problemDetailsMappingPolicy = problemDetailsMappingPolicy;
    this.securityProblemResponseWriter = securityProblemResponseWriter;
    this.maskedErrorDispatchDiagnosticsLogger = maskedErrorDispatchDiagnosticsLogger;
  }

  @Override
  public void handle(
      HttpServletRequest request,
      HttpServletResponse response,
      AccessDeniedException accessDeniedException) throws java.io.IOException {
    ProblemDetailsMappingPolicy.ProblemDefinition definition = problemDetailsMappingPolicy.mapSecurityAccessDenied();
    String traceId = securityProblemResponseWriter.resolveTraceId(request);
    maskedErrorDispatchDiagnosticsLogger.logIfPresent(request, traceId, accessDeniedException);
    if (log.isWarnEnabled()) {
      log.warn(
          "access_denied code={} path={} traceId={} exceptionType={}",
          ErrorCode.ACCESS_DENIED.code(),
          request.getRequestURI(),
          traceId,
          accessDeniedException == null ? "unknown" : accessDeniedException.getClass().getSimpleName());
    }
    securityProblemResponseWriter.writeProblem(request, response, definition);
  }
}