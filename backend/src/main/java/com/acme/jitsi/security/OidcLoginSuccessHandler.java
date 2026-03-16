package com.acme.jitsi.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

@Component
final class OidcLoginSuccessHandler implements AuthenticationSuccessHandler {

  private static final Logger log = LoggerFactory.getLogger(OidcLoginSuccessHandler.class);

  private final ProblemResponseFacade problemResponseFacade;
  private final String frontendOrigin;

  OidcLoginSuccessHandler(
      ProblemResponseFacade problemResponseFacade,
      @Value("${app.frontend.origin:http://localhost:3000}") String frontendOrigin) {
    this.problemResponseFacade = problemResponseFacade;
    this.frontendOrigin = frontendOrigin;
  }

  @Override
  public void onAuthenticationSuccess(
      HttpServletRequest request,
      HttpServletResponse response,
      Authentication authentication) throws java.io.IOException {
    String traceId = problemResponseFacade.resolveTraceId(request);
    String subjectId = authentication instanceof OAuth2AuthenticationToken token
        && token.getPrincipal() instanceof OAuth2User user
        ? user.getName()
        : authentication.getName();
    if (log.isInfoEnabled()) {
      log.info(
          "sso_login_event eventType=SSO_LOGIN_SUCCEEDED result=success subjectId={} traceId={} path={}",
          subjectId,
          traceId,
          request.getRequestURI());
    }
    response.sendRedirect(frontendOrigin + "/auth/continue");
  }
}