package com.acme.jitsi.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.InsufficientAuthenticationException;

class JsonSecurityAuthenticationEntryPointTest {

  private final ProblemDetailsMappingPolicy problemDetailsMappingPolicy = mock(ProblemDetailsMappingPolicy.class);
  private final SecurityProblemResponseWriter securityProblemResponseWriter = mock(SecurityProblemResponseWriter.class);
  private final JsonSecurityAuthenticationEntryPoint entryPoint =
      new JsonSecurityAuthenticationEntryPoint(problemDetailsMappingPolicy, securityProblemResponseWriter);

  @Test
  void writesMappedAuthenticationRequiredProblem() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/rooms");
    MockHttpServletResponse response = new MockHttpServletResponse();
    ProblemDetailsMappingPolicy.ProblemDefinition definition = new ProblemDetailsMappingPolicy.ProblemDefinition(
        HttpStatus.UNAUTHORIZED,
        "Authentication required",
        "Authentication is required to access this resource.",
        "AUTH_REQUIRED");
    when(problemDetailsMappingPolicy.mapSecurityAuthRequired()).thenReturn(definition);
    when(securityProblemResponseWriter.resolveTraceId(any(HttpServletRequest.class))).thenReturn("trace-entry-point");

    entryPoint.commence(request, response, new InsufficientAuthenticationException("auth required"));

    verify(securityProblemResponseWriter).writeProblem(request, response, definition);
    assertThat(response.getStatus()).isEqualTo(200);
  }
}