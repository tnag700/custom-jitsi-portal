package com.acme.jitsi.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.acme.jitsi.shared.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

@Component
final class OidcLoginFailureHandler implements AuthenticationFailureHandler {

  private static final Logger log = LoggerFactory.getLogger(OidcLoginFailureHandler.class);

  private final ProblemResponseFacade problemResponseFacade;
  private final String authErrorPath;

  OidcLoginFailureHandler(
      ProblemResponseFacade problemResponseFacade,
      @Value("${app.security.auth-error-path:/api/v1/auth/error}") String authErrorPath) {
    this.problemResponseFacade = problemResponseFacade;
    this.authErrorPath = normalizeAuthErrorPath(authErrorPath);
  }

  @Override
  public void onAuthenticationFailure(
      HttpServletRequest request,
      HttpServletResponse response,
      AuthenticationException exception) throws java.io.IOException {
    String traceId = problemResponseFacade.resolveTraceId(request);
    Throwable cause = exception == null ? null : exception.getCause();
    String exceptionType = exception == null ? "unknown" : exception.getClass().getSimpleName();
    String exceptionMessage = exception == null ? "" : exception.getMessage();
    String causeType = cause == null ? "" : cause.getClass().getSimpleName();
    String causeMessage = cause == null ? "" : cause.getMessage();
    if (log.isWarnEnabled()) {
      log.warn(
          "sso_login_event eventType=SSO_LOGIN_FAILED result=fail errorCode={} path={} traceId={} exceptionType={} exceptionMessage={} causeType={} causeMessage={}",
          ErrorCode.ACCESS_DENIED.code(),
          request.getRequestURI(),
          traceId,
          exceptionType,
          exceptionMessage,
          causeType,
          causeMessage);
    }
    response.sendRedirect(buildAuthErrorRedirect(request));
  }

  private String buildAuthErrorRedirect(HttpServletRequest request) {
    return UriComponentsBuilder.newInstance()
        .path(request.getContextPath())
        .path(authErrorPath)
        .queryParam("code", ErrorCode.ACCESS_DENIED.code())
        .build()
        .toUriString();
  }

  private static String normalizeAuthErrorPath(String configuredPath) {
    if (configuredPath == null || configuredPath.isBlank()) {
      return "/api/v1/auth/error";
    }
    return configuredPath.startsWith("/") ? configuredPath : "/" + configuredPath;
  }
}