package com.acme.jitsi.security;

import com.acme.jitsi.domains.meetings.service.MeetingTokenException;
import org.springframework.http.HttpStatus;

public interface ProblemDetailsMappingPolicy {

  ProblemDefinition mapAuthErrorCode(String code);

  ProblemDefinition mapSecurityAuthRequired();

  ProblemDefinition mapSecurityAccessDenied();

  ProblemDefinition mapMeetingTokenException(MeetingTokenException exception);

  String resolveValidationErrorCode(String requestUri);

  record ProblemDefinition(
      HttpStatus status,
      String title,
      String detail,
      String errorCode) {
  }
}
