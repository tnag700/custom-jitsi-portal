package com.acme.jitsi.security;

import com.acme.jitsi.shared.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class ApiValidationExceptionHandler {

  private final ProblemDetailsMappingPolicy problemDetailsMappingPolicy;
  private final ProblemResponseFacade problemResponseFacade;

  public ApiValidationExceptionHandler(
      ProblemDetailsMappingPolicy problemDetailsMappingPolicy,
      ProblemResponseFacade problemResponseFacade) {
    this.problemDetailsMappingPolicy = problemDetailsMappingPolicy;
    this.problemResponseFacade = problemResponseFacade;
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ProblemDetail handleMethodArgumentNotValid(MethodArgumentNotValidException ex, HttpServletRequest request) {
    List<FieldError> fieldErrors = new ArrayList<>(ex.getFieldErrors());
    FieldError firstFieldError = ex.getFieldError();
    String detail = buildFieldErrorsDetail(fieldErrors);
    if (detail.isBlank()) {
      if (firstFieldError != null && firstFieldError.getDefaultMessage() != null) {
        detail = firstFieldError.getDefaultMessage();
      } else {
        detail = "Проверьте корректность параметров запроса.";
      }
    }

    String requestUri = request.getRequestURI();
    String errorCode = resolveValidationErrorCode(fieldErrors, requestUri);
    return problemResponseFacade.buildProblemDetail(
        request,
        HttpStatus.BAD_REQUEST,
        "Ошибка валидации",
        detail,
        errorCode);
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ProblemDetail handleHttpMessageNotReadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
    String requestUri = request.getRequestURI();
    String errorCode = problemDetailsMappingPolicy.resolveValidationErrorCode(requestUri);
    return problemResponseFacade.buildProblemDetail(
        request,
        HttpStatus.BAD_REQUEST,
        "Некорректный запрос",
        "Проверьте корректность тела запроса.",
        errorCode);
  }

        @ExceptionHandler(MissingRequestHeaderException.class)
        public ProblemDetail handleMissingRequestHeader(MissingRequestHeaderException ex, HttpServletRequest request) {
          String requestUri = request.getRequestURI();
          String errorCode = problemDetailsMappingPolicy.resolveValidationErrorCode(requestUri);
          return problemResponseFacade.buildProblemDetail(
          request,
          HttpStatus.BAD_REQUEST,
          "Ошибка валидации",
          "Отсутствует обязательный заголовок: " + ex.getHeaderName(),
          errorCode);
        }

  private String resolveValidationErrorCode(List<FieldError> fieldErrors, String requestUri) {
    boolean hasRoleError = hasRoleFieldError(fieldErrors);
    if (hasRoleError && isMeetingsApiRequest(requestUri)) {
      return ErrorCode.INVALID_ROLE.code();
    }

    return problemDetailsMappingPolicy.resolveValidationErrorCode(requestUri);
  }

  private boolean isMeetingsApiRequest(String requestUri) {
    return requestUri != null && requestUri.startsWith("/api/v1/meetings/");
  }

  private String formatFieldError(FieldError error) {
    String field = error.getField();
    String message = error.getDefaultMessage();
    return field + ": " + message;
  }

  private String buildFieldErrorsDetail(List<FieldError> fieldErrors) {
    StringBuilder detailBuilder = new StringBuilder();
    for (FieldError fieldError : fieldErrors) {
      if (!detailBuilder.isEmpty()) {
        detailBuilder.append("; ");
      }
      detailBuilder.append(formatFieldError(fieldError));
    }
    return detailBuilder.toString();
  }

  private boolean hasRoleFieldError(List<FieldError> fieldErrors) {
    for (FieldError fieldError : fieldErrors) {
      String field = fieldError.getField();
      if ("role".equals(field)) {
        return true;
      }
    }
    return false;
  }
}