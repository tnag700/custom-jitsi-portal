package com.acme.jitsi.infrastructure.idempotency;

import static org.assertj.core.api.Assertions.assertThat;

import com.acme.jitsi.security.ProblemDetailsFactory;
import com.acme.jitsi.security.ProblemResponseFacade;
import com.acme.jitsi.shared.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.mock.web.MockHttpServletRequest;

class IdempotencyExceptionHandlerTest {

  private final IdempotencyExceptionHandler handler =
      new IdempotencyExceptionHandler(new ProblemResponseFacade(new ProblemDetailsFactory()));

  @Test
  void idempotencyConflictResponseDoesNotLeakRawHeaderValue() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRequestURI("/api/v1/test/idempotent");
    request.addHeader("X-Trace-Id", "trace-idem-conflict-1");

    ProblemDetail detail =
        handler.handleIdempotencyConflictException(
            new IdempotencyConflictException(
                "Request with Idempotency-Key secret-client-token is already being processed."),
            request);

    assertThat(detail.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
    assertThat(detail.getDetail())
        .isEqualTo(
            "Request with the same Idempotency-Key is already being processed or has already been processed.")
        .doesNotContain("secret-client-token");
    assertThat(detail.getProperties()).containsEntry("errorCode", ErrorCode.IDEMPOTENCY_CONFLICT.code());
    assertThat(detail.getProperties()).containsEntry("traceId", "trace-idem-conflict-1");
  }

  @Test
  void invalidIdempotencyKeyResponseKeepsSafeValidationMessage() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRequestURI("/api/v1/test/idempotent");
    request.addHeader("X-Trace-Id", "trace-idem-invalid-1");

    ProblemDetail detail =
        handler.handleInvalidIdempotencyKeyException(
            new InvalidIdempotencyKeyException("Idempotency-Key contains unsupported characters"),
            request);

    assertThat(detail.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
    assertThat(detail.getDetail()).isEqualTo("Idempotency-Key contains unsupported characters");
    assertThat(detail.getProperties()).containsEntry("errorCode", ErrorCode.IDEMPOTENCY_KEY_INVALID.code());
    assertThat(detail.getProperties()).containsEntry("traceId", "trace-idem-invalid-1");
  }
}