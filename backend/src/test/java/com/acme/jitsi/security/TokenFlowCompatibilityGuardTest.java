package com.acme.jitsi.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.acme.jitsi.domains.configsets.service.ConfigSetCompatibilityCheck;
import com.acme.jitsi.domains.configsets.service.ConfigSetCompatibilityStateService;
import com.acme.jitsi.domains.meetings.service.MeetingTokenException;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TokenFlowCompatibilityGuardTest {

  @Mock
  private ConfigSetCompatibilityStateService compatibilityStateService;

  @Test
  void assertTokenFlowsAllowedThrowsWhenIncompatibleConfigExists() {
    when(compatibilityStateService.findLatestIncompatibleActive()).thenReturn(Optional.of(
        new ConfigSetCompatibilityCheck(
            "chk-1",
            "cs-1",
            false,
            java.util.List.of("ISSUER_MISMATCH"),
            "issuer mismatch",
            Instant.parse("2026-01-01T00:00:00Z"),
            "trace-1")));

    TokenFlowCompatibilityGuard guard = new TokenFlowCompatibilityGuard(compatibilityStateService);

    assertThatThrownBy(guard::assertTokenFlowsAllowed)
        .isInstanceOf(MeetingTokenException.class)
        .extracting(error -> ((MeetingTokenException) error).errorCode())
        .isEqualTo("CONFIG_INCOMPATIBLE");
  }

  @Test
  void assertTokenFlowsAllowedPassesWhenNoIncompatibility() {
    when(compatibilityStateService.findLatestIncompatibleActive()).thenReturn(Optional.empty());

    TokenFlowCompatibilityGuard guard = new TokenFlowCompatibilityGuard(compatibilityStateService);

    assertThatCode(guard::assertTokenFlowsAllowed).doesNotThrowAnyException();
  }
}
