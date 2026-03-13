package com.acme.jitsi.security;

import org.springframework.http.HttpStatus;

public interface ProblemDetailsMappingPolicy {

  ProblemDefinition mapAuthErrorCode(String code);

  ProblemDefinition mapSecurityAuthRequired();

  ProblemDefinition mapSecurityAccessDenied();

  ProblemDefinition mapTokenException(HttpStatus status, String errorCode, String detail);

  String resolveValidationErrorCode(String requestUri);

  record ProblemDefinition(
      HttpStatus status,
      String title,
      String detail,
      String errorCode) {
  }
}
