package com.acme.jitsi.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;

class OidcLoginFailureHandlerTest {

  @Test
  void redirectsToStableAuthErrorEndpointOnOidcLoginFailure() throws Exception {
    ProblemResponseFacade problemResponseFacade = mock(ProblemResponseFacade.class);
    when(problemResponseFacade.resolveTraceId(any(HttpServletRequest.class))).thenReturn("trace-login-failure");
    OidcLoginFailureHandler handler = new OidcLoginFailureHandler(problemResponseFacade, "/api/v1/auth/error");
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/login/oauth2/code/keycloak");
    MockHttpServletResponse response = new MockHttpServletResponse();

    handler.onAuthenticationFailure(
        request,
        response,
        new OAuth2AuthenticationException(new OAuth2Error("invalid_token", "issuer mismatch", null), "issuer mismatch"));

    assertThat(response.getRedirectedUrl()).isEqualTo("/api/v1/auth/error?code=ACCESS_DENIED");
  }

  @Test
  void redirectsUsingContextPathAndConfiguredAuthErrorRoute() throws Exception {
    ProblemResponseFacade problemResponseFacade = mock(ProblemResponseFacade.class);
    when(problemResponseFacade.resolveTraceId(any(HttpServletRequest.class))).thenReturn("trace-login-failure");
    OidcLoginFailureHandler handler = new OidcLoginFailureHandler(problemResponseFacade, "/api/v2/auth/error");
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/edge/login/oauth2/code/keycloak");
    request.setContextPath("/edge");
    MockHttpServletResponse response = new MockHttpServletResponse();

    handler.onAuthenticationFailure(
        request,
        response,
        new OAuth2AuthenticationException(new OAuth2Error("invalid_token", "issuer mismatch", null), "issuer mismatch"));

    assertThat(response.getRedirectedUrl()).isEqualTo("/edge/api/v2/auth/error?code=ACCESS_DENIED");
  }
}