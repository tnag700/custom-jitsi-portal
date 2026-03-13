package com.acme.jitsi.domains.meetings.api;

import com.acme.jitsi.shared.ErrorCode;

import static org.assertj.core.api.Assertions.assertThat;

import com.acme.jitsi.security.ApiValidationExceptionHandler;
import com.acme.jitsi.security.DefaultProblemDetailsMappingPolicy;
import com.acme.jitsi.security.ProblemResponseFacade;
import com.acme.jitsi.security.ProblemDetailsFactory;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.ProblemDetail;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;

class MeetingTokenExceptionHandlerTest {

  private final ApiValidationExceptionHandler handler =
      new ApiValidationExceptionHandler(
          new DefaultProblemDetailsMappingPolicy(),
          new ProblemResponseFacade(new ProblemDetailsFactory()));

  @Test
  void methodArgumentNotValidBuildsRfc7807ProblemDetail() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRequestURI("/api/v1/meetings/meeting-a/access-token");
    request.addHeader("X-Trace-Id", "trace-method-arg-1");

    BeanPropertyBindingResult bindingResult =
        new BeanPropertyBindingResult(new DummyRequest(), "dummyRequest");
    bindingResult.addError(new FieldError("dummyRequest", "meetingId", "meetingId is required"));

    MethodParameter parameter =
        new MethodParameter(
            DummyController.class.getDeclaredMethod("handle", DummyRequest.class),
            0);
    MethodArgumentNotValidException exception =
        new MethodArgumentNotValidException(parameter, bindingResult);

    ProblemDetail detail = handler.handleMethodArgumentNotValid(exception, request);

    assertThat(detail.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
    assertThat(detail.getTitle()).isEqualTo("Ошибка валидации");
    assertThat(detail.getDetail()).isEqualTo("meetingId: meetingId is required");
    assertThat(detail.getInstance()).hasToString("/api/v1/meetings/meeting-a/access-token");
    assertThat(detail.getProperties()).containsEntry("errorCode", ErrorCode.INVALID_REQUEST.code());
    assertThat(detail.getProperties()).containsEntry("traceId", "trace-method-arg-1");
  }

  @Test
  void httpMessageNotReadableBuildsRfc7807ProblemDetail() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRequestURI("/api/v1/meetings/meeting-b/access-token");
    request.addHeader("X-Trace-Id", "trace-not-readable-1");

    HttpMessageNotReadableException exception =
      new HttpMessageNotReadableException("Malformed JSON request", new MockHttpInputMessage(new byte[0]));

    ProblemDetail detail = handler.handleHttpMessageNotReadable(exception, request);

    assertThat(detail.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
    assertThat(detail.getTitle()).isEqualTo("Некорректный запрос");
    assertThat(detail.getDetail()).isEqualTo("Проверьте корректность тела запроса.");
    assertThat(detail.getInstance()).hasToString("/api/v1/meetings/meeting-b/access-token");
    assertThat(detail.getProperties()).containsEntry("errorCode", ErrorCode.INVALID_REQUEST.code());
    assertThat(detail.getProperties()).containsEntry("traceId", "trace-not-readable-1");
  }

  @Test
  void methodArgumentNotValidForMeetingRoleReturnsInvalidRole() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRequestURI("/api/v1/meetings/meeting-a/participants/user-1");

    BeanPropertyBindingResult bindingResult =
        new BeanPropertyBindingResult(new DummyRequest(), "dummyRequest");
    bindingResult.addError(new FieldError("dummyRequest", "role", "role must be host/moderator/participant"));

    MethodParameter parameter =
        new MethodParameter(
            DummyController.class.getDeclaredMethod("handle", DummyRequest.class),
            0);
    MethodArgumentNotValidException exception =
        new MethodArgumentNotValidException(parameter, bindingResult);

    ProblemDetail detail = handler.handleMethodArgumentNotValid(exception, request);

    assertThat(detail.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
    assertThat(detail.getProperties()).containsEntry("errorCode", ErrorCode.INVALID_ROLE.code());
  }

  @Test
  void missingRequestHeaderBuildsRfc7807ProblemDetail() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRequestURI("/api/v1/meetings/meeting-c/access-token");
    request.addHeader("X-Trace-Id", "trace-missing-header-1");

    MethodParameter parameter =
        new MethodParameter(
            DummyController.class.getDeclaredMethod("handle", DummyRequest.class),
            0);
    MissingRequestHeaderException exception =
        new MissingRequestHeaderException("Idempotency-Key", parameter);

    ProblemDetail detail = handler.handleMissingRequestHeader(exception, request);

    assertThat(detail.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
    assertThat(detail.getTitle()).isEqualTo("Ошибка валидации");
    assertThat(detail.getDetail()).isEqualTo("Отсутствует обязательный заголовок: Idempotency-Key");
    assertThat(detail.getInstance()).hasToString("/api/v1/meetings/meeting-c/access-token");
    assertThat(detail.getProperties()).containsEntry("errorCode", ErrorCode.INVALID_REQUEST.code());
    assertThat(detail.getProperties()).containsEntry("traceId", "trace-missing-header-1");
  }

  private static final class DummyController {

    @SuppressWarnings("unused")
    void handle(DummyRequest request) {
    }
  }

  private static final class DummyRequest {
  }
}


