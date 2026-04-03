package com.acme.jitsi.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;

class OidcLoginSuccessHandlerTest {

  @Test
  void logsSuccessWithoutAccessDeniedErrorCode() throws Exception {
    ProblemResponseFacade problemResponseFacade = mock(ProblemResponseFacade.class);
    when(problemResponseFacade.resolveTraceId(any(HttpServletRequest.class))).thenReturn("trace-login-success");
    OidcLoginSuccessHandler handler = new OidcLoginSuccessHandler(problemResponseFacade, "http://localhost:3000");
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/login/oauth2/code/keycloak");
    MockHttpServletResponse response = new MockHttpServletResponse();
    TestingAuthenticationToken authentication = new TestingAuthenticationToken("user-1", "n/a");

    Logger logger = (Logger) LoggerFactory.getLogger(OidcLoginSuccessHandler.class);
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);

    try {
      handler.onAuthenticationSuccess(request, response, authentication);

      assertThat(response.getRedirectedUrl()).isEqualTo("http://localhost:3000/auth/continue");
      assertThat(appender.list).isNotEmpty();
      String message = appender.list.getFirst().getFormattedMessage();
      assertThat(message).contains("eventType=SSO_LOGIN_SUCCEEDED");
      assertThat(message).contains("result=success");
      assertThat(message).contains("subjectId=user-1");
      assertThat(message).contains("traceId=trace-login-success");
      assertThat(message).doesNotContain("ACCESS_DENIED");
      assertThat(message).doesNotContain("errorCode=");
    } finally {
      logger.detachAppender(appender);
      appender.stop();
    }
  }

  @Test
  void redirectsToRememberedReturnToAndClearsSessionAttribute() throws Exception {
    ProblemResponseFacade problemResponseFacade = mock(ProblemResponseFacade.class);
    OidcLoginSuccessHandler handler = new OidcLoginSuccessHandler(problemResponseFacade, "http://localhost:3000");
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/login/oauth2/code/keycloak");
    request.getSession(true)
        .setAttribute(
            AuthRedirectFlowSupport.RETURN_TO_SESSION_ATTRIBUTE,
            "/admin/config-sets?environment=dev");
    MockHttpServletResponse response = new MockHttpServletResponse();
    TestingAuthenticationToken authentication = new TestingAuthenticationToken("user-1", "n/a");

    handler.onAuthenticationSuccess(request, response, authentication);

    assertThat(response.getRedirectedUrl())
        .isEqualTo("http://localhost:3000/auth/continue?returnTo=%2Fadmin%2Fconfig-sets%3Fenvironment%3Ddev");
    assertThat(request.getSession(false).getAttribute(AuthRedirectFlowSupport.RETURN_TO_SESSION_ATTRIBUTE))
        .isNull();
  }
}