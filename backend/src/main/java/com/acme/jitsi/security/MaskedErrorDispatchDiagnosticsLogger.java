package com.acme.jitsi.security;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
final class MaskedErrorDispatchDiagnosticsLogger {

  private static final Logger log = LoggerFactory.getLogger(MaskedErrorDispatchDiagnosticsLogger.class);

  void logIfPresent(HttpServletRequest request, String traceId, Exception accessDeniedException) {
    if (!"/error".equals(request.getRequestURI())) {
      return;
    }

    Object statusCode = request.getAttribute("jakarta.servlet.error.status_code");
    Object originUri = request.getAttribute("jakarta.servlet.error.request_uri");
    Object originException = request.getAttribute("jakarta.servlet.error.exception");
    String originExceptionType = resolveExceptionType(originException);
    String originExceptionMessage = originException == null ? "" : String.valueOf(originException);
    String securityExceptionType = resolveExceptionType(accessDeniedException);
    if (log.isErrorEnabled()) {
      log.error(
          "masked_error_dispatch path=/error traceId={} originalStatus={} originalPath={} originalExceptionType={} originalException={} securityExceptionType={}",
          traceId,
          statusCode,
          originUri,
          originExceptionType,
          originExceptionMessage,
          securityExceptionType);
    }
  }

  private String resolveExceptionType(Object exceptionObject) {
    if (exceptionObject == null) {
      return "unknown";
    }
    String simpleName = exceptionObject.getClass().getSimpleName();
    if (simpleName != null && !simpleName.isBlank()) {
      return simpleName;
    }
    return exceptionObject.getClass().getName();
  }
}