package com.acme.jitsi.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springframework.web.util.UriComponentsBuilder;

public final class AuthRedirectFlowSupport {

  public static final String RETURN_TO_SESSION_ATTRIBUTE =
      AuthRedirectFlowSupport.class.getName() + ".returnTo";

  private AuthRedirectFlowSupport() {}

  public static void rememberReturnTo(HttpServletRequest request, String returnTo) {
    String safeReturnTo = sanitizeReturnTo(returnTo);
    if (safeReturnTo == null) {
      clearRememberedReturnTo(request);
      return;
    }
    request.getSession(true).setAttribute(RETURN_TO_SESSION_ATTRIBUTE, safeReturnTo);
  }

  public static String consumeReturnTo(HttpServletRequest request) {
    HttpSession session = request.getSession(false);
    if (session == null) {
      return null;
    }
    Object returnTo = session.getAttribute(RETURN_TO_SESSION_ATTRIBUTE);
    session.removeAttribute(RETURN_TO_SESSION_ATTRIBUTE);
    return returnTo instanceof String stringValue ? sanitizeReturnTo(stringValue) : null;
  }

  public static String buildFrontendContinueRedirect(String frontendOrigin, String returnTo) {
    String redirectBase = UriComponentsBuilder.fromUriString(frontendOrigin)
        .path("/auth/continue")
        .build()
        .toUriString();
    String safeReturnTo = sanitizeReturnTo(returnTo);
    if (safeReturnTo == null) {
      return redirectBase;
    }
    return redirectBase + "?returnTo=" + URLEncoder.encode(safeReturnTo, StandardCharsets.UTF_8);
  }

  public static String sanitizeReturnTo(String returnTo) {
    if (returnTo == null) {
      return null;
    }
    String trimmed = returnTo.trim();
    if (trimmed.isEmpty() || !trimmed.startsWith("/") || trimmed.startsWith("//")) {
      return null;
    }
    return trimmed;
  }

  private static void clearRememberedReturnTo(HttpServletRequest request) {
    HttpSession session = request.getSession(false);
    if (session != null) {
      session.removeAttribute(RETURN_TO_SESSION_ATTRIBUTE);
    }
  }
}