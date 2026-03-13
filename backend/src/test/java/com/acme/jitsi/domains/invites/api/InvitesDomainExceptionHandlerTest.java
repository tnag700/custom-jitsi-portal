package com.acme.jitsi.domains.invites.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.acme.jitsi.domains.invites.service.InviteExchangeException;
import com.acme.jitsi.security.DefaultProblemDetailsMappingPolicy;
import com.acme.jitsi.security.ProblemDetailsFactory;
import com.acme.jitsi.security.ProblemResponseFacade;
import com.acme.jitsi.shared.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.mock.web.MockHttpServletRequest;

class InvitesDomainExceptionHandlerTest {

  private final InvitesDomainExceptionHandler handler =
      new InvitesDomainExceptionHandler(
          new DefaultProblemDetailsMappingPolicy(),
          new ProblemResponseFacade(new ProblemDetailsFactory()));

  @Test
  void inviteNotFoundMapsTo404InviteNotFound() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRequestURI("/api/v1/invites/exchange");
    request.addHeader("X-Trace-Id", "trace-invite-not-found");

    ProblemDetail detail =
      handler.handleInviteExchangeException(
        new InviteExchangeException(HttpStatus.NOT_FOUND, ErrorCode.INVITE_NOT_FOUND.code(), "missing-token"),
        request);

    assertThat(detail.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
    assertThat(detail.getProperties()).containsEntry("errorCode", ErrorCode.INVITE_NOT_FOUND.code());
    assertThat(detail.getProperties()).containsEntry("traceId", "trace-invite-not-found");
  }

  @Test
  void inviteRevokedMapsTo410InviteRevoked() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRequestURI("/api/v1/invites/exchange");
    request.addHeader("X-Trace-Id", "trace-invite-revoked");

    ProblemDetail detail =
      handler.handleInviteExchangeException(
        new InviteExchangeException(HttpStatus.GONE, ErrorCode.INVITE_REVOKED.code(), "revoked-token"),
        request);

    assertThat(detail.getStatus()).isEqualTo(HttpStatus.GONE.value());
    assertThat(detail.getProperties()).containsEntry("errorCode", ErrorCode.INVITE_REVOKED.code());
    assertThat(detail.getProperties()).containsEntry("traceId", "trace-invite-revoked");
  }

  @Test
  void inviteExpiredMapsTo410InviteExpired() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRequestURI("/api/v1/invites/exchange");
    request.addHeader("X-Trace-Id", "trace-invite-expired");

    ProblemDetail detail =
      handler.handleInviteExchangeException(
        new InviteExchangeException(HttpStatus.GONE, ErrorCode.INVITE_EXPIRED.code(), "expired-token"),
        request);

    assertThat(detail.getStatus()).isEqualTo(HttpStatus.GONE.value());
    assertThat(detail.getProperties()).containsEntry("errorCode", ErrorCode.INVITE_EXPIRED.code());
    assertThat(detail.getProperties()).containsEntry("traceId", "trace-invite-expired");
  }
}
