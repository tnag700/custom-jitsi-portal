package com.acme.jitsi.security;

import com.acme.jitsi.shared.ErrorCode;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Scope;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.mock.web.MockHttpServletRequest;

class ProblemResponseFacadeTest {

  private final ProblemDetailsFactory problemDetailsFactory = new ProblemDetailsFactory();
  private final ProblemResponseFacade facade = new ProblemResponseFacade(problemDetailsFactory);

  @Test
  void buildsProblemPayloadWithStableRfc7807Structure() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRequestURI("/api/v1/meetings/meeting-a/access-token");
    request.addHeader("X-Trace-Id", "trace-123");

    Map<String, Object> payload =
        facade.buildProblemPayload(
            request,
            HttpStatus.FORBIDDEN,
            "Доступ запрещен",
            "Недостаточно прав для выполнения операции.",
            ErrorCode.ACCESS_DENIED.code());

    assertThat(payload)
        .containsEntry("type", "about:blank")
        .containsEntry("title", "Доступ запрещен")
        .containsEntry("status", 403)
        .containsEntry("detail", "Недостаточно прав для выполнения операции.")
        .containsEntry("instance", "/api/v1/meetings/meeting-a/access-token");

    assertThat(payload.get("properties")).isInstanceOf(Map.class);
    @SuppressWarnings("unchecked")
    Map<String, Object> properties = (Map<String, Object>) payload.get("properties");
    assertThat(properties).containsEntry("errorCode", ErrorCode.ACCESS_DENIED.code());
    assertThat(properties).containsEntry("traceId", "trace-123");
    assertThat(properties).containsEntry("requestId", "trace-123");
  }

  @Test
  void buildsProblemDetailWithStableInstanceAndProperties() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRequestURI("/api/v1/meetings/meeting-conflict/access-token");
    request.addHeader("X-Trace-Id", "trace-456");

    ProblemDetail detail =
        facade.buildProblemDetail(
            request,
            HttpStatus.CONFLICT,
            "Конфликт запроса",
            "Назначение роли неоднозначно.",
        ErrorCode.ROLE_MISMATCH.code());

    assertThat(detail.getStatus()).isEqualTo(409);
    assertThat(detail.getTitle()).isEqualTo("Конфликт запроса");
    assertThat(detail.getDetail()).isEqualTo("Назначение роли неоднозначно.");
    assertThat(detail.getInstance()).hasToString("/api/v1/meetings/meeting-conflict/access-token");
    assertThat(detail.getProperties()).containsEntry("errorCode", ErrorCode.ROLE_MISMATCH.code());
    assertThat(detail.getProperties()).containsEntry("traceId", "trace-456");
    assertThat(detail.getProperties()).containsEntry("requestId", "trace-456");
  }

  @Test
  void resolvesTraceIdConsistentlyForSameRequestWithoutHeader() {
    HttpServletRequest request = new MockHttpServletRequest();

    String first = facade.resolveTraceId(request);
    String second = facade.resolveTraceId(request);

    assertThat(first).isNotBlank();
    assertThat(second).isEqualTo(first);
  }

  @Test
  void ignoresUnsafeTraceIdHeaderValue() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("X-Trace-Id", "bad\ntrace");

    String traceId = facade.resolveTraceId(request);
    String requestId = facade.resolveRequestId(request);

    assertThat(traceId).isNotBlank();
    assertThat(traceId).doesNotContain("\n");
    assertThat(traceId).isNotEqualTo("bad\ntrace");
    assertThat(requestId).isNotBlank();
    assertThat(requestId).doesNotContain("\n");
    assertThat(requestId).isNotEqualTo("bad\ntrace");
  }

  @Test
  void prefersActiveSpanTraceIdAndPreservesHeaderAsRequestId() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("X-Trace-Id", "client-request-123");
    Span span = Span.wrap(SpanContext.create(
        "0123456789abcdef0123456789abcdef",
        "0123456789abcdef",
        TraceFlags.getSampled(),
        TraceState.getDefault()));

    try (Scope ignored = span.makeCurrent()) {
      assertThat(facade.resolveTraceId(request)).isEqualTo("0123456789abcdef0123456789abcdef");
      assertThat(facade.resolveRequestId(request)).isEqualTo("client-request-123");
    }
  }
}


