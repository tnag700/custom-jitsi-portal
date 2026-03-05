package com.acme.jitsi.security;

import com.acme.jitsi.domains.configsets.service.ConfigSetCompatibilityStateService;
import com.acme.jitsi.domains.meetings.service.MeetingTokenException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class TokenFlowCompatibilityGuard {

  private final ConfigSetCompatibilityStateService compatibilityStateService;

  public TokenFlowCompatibilityGuard(ConfigSetCompatibilityStateService compatibilityStateService) {
    this.compatibilityStateService = compatibilityStateService;
  }

  public void assertTokenFlowsAllowed() {
    compatibilityStateService.findLatestIncompatibleActive().ifPresent(check -> {
      throw new MeetingTokenException(
          HttpStatus.INTERNAL_SERVER_ERROR,
          "CONFIG_INCOMPATIBLE",
          "Token flow is blocked due to incompatible active config set: " + check.configSetId());
    });
  }
}