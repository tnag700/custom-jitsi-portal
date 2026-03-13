package com.acme.jitsi.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;

class JsonSecurityAccessDeniedHandlerTest {

  private final ProblemDetailsMappingPolicy problemDetailsMappingPolicy = mock(ProblemDetailsMappingPolicy.class);
  private final SecurityProblemResponseWriter securityProblemResponseWriter = mock(SecurityProblemResponseWriter.class);
  private final MaskedErrorDispatchDiagnosticsLogger maskedErrorDispatchDiagnosticsLogger =
      mock(MaskedErrorDispatchDiagnosticsLogger.class);
  private final JsonSecurityAccessDeniedHandler handler =
      new JsonSecurityAccessDeniedHandler(
          problemDetailsMappingPolicy,
          securityProblemResponseWriter,
          maskedErrorDispatchDiagnosticsLogger);

  @Test
  void logsMaskedErrorDispatchAndWritesMappedAccessDeniedProblem() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/error");
    MockHttpServletResponse response = new MockHttpServletResponse();
    AccessDeniedException exception = new AccessDeniedException("denied");
    ProblemDetailsMappingPolicy.ProblemDefinition definition = new ProblemDetailsMappingPolicy.ProblemDefinition(
        HttpStatus.FORBIDDEN,
        "Access denied",
        "You do not have permission to access this resource.",
        "ACCESS_DENIED");
    when(problemDetailsMappingPolicy.mapSecurityAccessDenied()).thenReturn(definition);
    when(securityProblemResponseWriter.resolveTraceId(any(HttpServletRequest.class))).thenReturn("trace-access-denied");

    handler.handle(request, response, exception);

    verify(maskedErrorDispatchDiagnosticsLogger).logIfPresent(request, "trace-access-denied", exception);
    verify(securityProblemResponseWriter).writeProblem(request, response, definition);
  }
}