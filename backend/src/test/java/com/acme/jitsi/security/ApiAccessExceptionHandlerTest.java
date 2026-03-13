package com.acme.jitsi.security;

import com.acme.jitsi.shared.ErrorCode;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;

class ApiAccessExceptionHandlerTest {

  private final ProblemResponseFacade problemResponseFacade =
      new ProblemResponseFacade(new ProblemDetailsFactory());
  private final ApiAccessExceptionHandler handler =
      new ApiAccessExceptionHandler(problemResponseFacade);

  @Test
  void tenantClaimMissingMapsToTenantClaimRequiredCode() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRequestURI("/api/v1/rooms");
    request.addHeader("X-Trace-Id", "trace-tenant-missing");

    var detail = handler.handleAccessDenied(new AccessDeniedException("Tenant claim is required"), request);

    assertThat(detail.getStatus()).isEqualTo(403);
    assertThat(detail.getProperties()).containsEntry("errorCode", ErrorCode.TENANT_CLAIM_REQUIRED.code());
    assertThat(detail.getProperties()).containsEntry("traceId", "trace-tenant-missing");
  }

  @Test
  void tenantMismatchMapsToTenantAccessDeniedCode() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRequestURI("/api/v1/rooms");
    request.addHeader("X-Trace-Id", "trace-tenant-mismatch");

    var detail = handler.handleAccessDenied(
        new AccessDeniedException("Requested tenant is not accessible for current principal"),
        request);

    assertThat(detail.getStatus()).isEqualTo(403);
    assertThat(detail.getProperties()).containsEntry("errorCode", ErrorCode.TENANT_ACCESS_DENIED.code());
    assertThat(detail.getProperties()).containsEntry("traceId", "trace-tenant-mismatch");
  }

  @Test
  void genericAccessDeniedMapsToAccessDeniedCode() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRequestURI("/api/v1/rooms");
    request.addHeader("X-Trace-Id", "trace-generic-denied");

    var detail = handler.handleAccessDenied(new AccessDeniedException("some other message"), request);

    assertThat(detail.getStatus()).isEqualTo(403);
    assertThat(detail.getProperties()).containsEntry("errorCode", ErrorCode.ACCESS_DENIED.code());
    assertThat(detail.getProperties()).containsEntry("traceId", "trace-generic-denied");
  }

}



