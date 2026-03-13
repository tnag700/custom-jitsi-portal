package com.acme.jitsi.shared;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ErrorCodeTest {

  @Test
  void accessDeniedCodeReturnsExpectedString() {
    assertThat(ErrorCode.ACCESS_DENIED.code()).isEqualTo("ACCESS_DENIED");
  }

  @Test
  void allErrorCodesAreNotNull() {
    for (ErrorCode errorCode : ErrorCode.values()) {
      assertThat(errorCode.code()).isNotNull();
    }
  }

  @Test
  void codeMatchesName() {
    for (ErrorCode errorCode : ErrorCode.values()) {
      assertThat(errorCode.code())
          .as("ErrorCode.%s.code() должен совпадать с name()", errorCode.name())
          .isEqualTo(errorCode.name());
    }
  }

  @Test
  void roleMismatchCodeIsRegistered() {
    assertThat(ErrorCode.ROLE_MISMATCH.code()).isEqualTo("ROLE_MISMATCH");
  }
}
