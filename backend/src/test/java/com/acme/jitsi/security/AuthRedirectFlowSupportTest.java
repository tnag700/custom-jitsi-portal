package com.acme.jitsi.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class AuthRedirectFlowSupportTest {

  @ParameterizedTest(name = "{0}")
  @MethodSource("unsafeReturnToValues")
  void rejectsUnsafeReturnToValues(String caseName, String returnTo) {
    assertThat(AuthRedirectFlowSupport.sanitizeReturnTo(returnTo)).isNull();
    assertThat(AuthRedirectFlowSupport.buildFrontendContinueRedirect(
            "https://portal.example", returnTo))
        .isEqualTo("https://portal.example/auth/continue");
  }

  @ParameterizedTest
  @ValueSource(strings = {
      "/",
      "/profile",
      "/admin/config-sets?environment=prod#status",
      "/meetings/?roomId=804f097a-60c7-487a-9078-40da53df2d87",
      "/receipt?discount=100%25",
      "/search?q=%D0%98%D0%B2%D0%B0%D0%BD%20%D0%98%D0%B2%D0%B0%D0%BD%D0%BE%D0%B2"
  })
  void preservesValidLocalReturnToValues(String returnTo) {
    assertThat(AuthRedirectFlowSupport.sanitizeReturnTo(returnTo)).isEqualTo(returnTo);
  }

  @Test
  void rejectsNullReturnTo() {
    assertThat(AuthRedirectFlowSupport.sanitizeReturnTo(null)).isNull();
  }

  private static Stream<Arguments> unsafeReturnToValues() {
    return Stream.of(
        arguments("absolute URI", "https://evil.example/phish"),
        arguments("network-path authority", "//evil.example/phish"),
        arguments("triple-slash authority", "///evil.example/phish"),
        arguments("backslash authority", "/\\evil.example/phish"),
        arguments("repeated-backslash authority", "/\\\\evil.example/phish"),
        arguments("encoded backslash", "/%5Cevil.example/phish"),
        arguments("lowercase encoded backslash", "/%5cevil.example/phish"),
        arguments("double-encoded backslash", "/%255Cevil.example/phish"),
        arguments("composed encoded backslash", "/%25%35%43evil.example/phish"),
        arguments("encoded network-path authority", "/%2F%2Fevil.example/phish"),
        arguments("encoded CRLF", "/profile%0D%0ALocation:%20https://evil.example"),
        arguments("double-encoded line feed", "/profile%250Aignored"),
        arguments("literal CRLF", "/profile\r\nLocation: https://evil.example"),
        arguments("literal null", "/profile" + Character.toString(0)),
        arguments("literal delete", "/profile" + Character.toString(127)),
        arguments("malformed percent escape", "/profile%ZZ"));
  }
}
