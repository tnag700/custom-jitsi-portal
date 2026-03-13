package com.acme.jitsi.security;

import com.acme.jitsi.shared.ErrorCode;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.mock.web.MockHttpServletRequest;

class ApiUnhandledExceptionHandlerTest {

  private final ProblemResponseFacade problemResponseFacade =
      new ProblemResponseFacade(new ProblemDetailsFactory());
  private final ApiUnhandledExceptionHandler handler =
      new ApiUnhandledExceptionHandler(problemResponseFacade);

  @Test
  void invalidPersistedStateReturnsExplicitCode() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRequestURI("/api/v1/rooms");
    request.addHeader("X-Trace-Id", "trace-invalid-persisted-state");

    var detail = handler.handleInvalidPersistedState(
        new InvalidDataAccessApiUsageException("No enum constant RoomStatus.active"),
        request);

    assertThat(detail.getStatus()).isEqualTo(500);
    assertThat(detail.getProperties()).containsEntry("errorCode", ErrorCode.INVALID_PERSISTED_STATE.code());
    assertThat(detail.getProperties()).containsEntry("traceId", "trace-invalid-persisted-state");
  }

  @Test
  void unexpectedExceptionReturnsInternalErrorCode() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRequestURI("/api/v1/auth/me");
    request.addHeader("X-Trace-Id", "trace-internal-error");

    var detail = handler.handleUnhandledException(new IllegalStateException("boom"), request);

    assertThat(detail.getStatus()).isEqualTo(500);
    assertThat(detail.getProperties()).containsEntry("errorCode", ErrorCode.INTERNAL_ERROR.code());
    assertThat(detail.getProperties()).containsEntry("traceId", "trace-internal-error");
  }
}


