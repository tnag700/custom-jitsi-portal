package com.acme.jitsi.security;

import com.acme.jitsi.domains.configsets.service.ConfigSetCompatibilityStateService;
import com.acme.jitsi.shared.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class TokenIssuanceCompatibilityPolicy {

  private final ConfigSetCompatibilityStateService compatibilityStateService;

  public TokenIssuanceCompatibilityPolicy(ConfigSetCompatibilityStateService compatibilityStateService) {
    this.compatibilityStateService = compatibilityStateService;
  }

  public void assertTokenIssuanceAllowed() {
    compatibilityStateService.findLatestIncompatibleActive().ifPresent(check -> {
      throw new TokenIssuancePolicyException(
          HttpStatus.INTERNAL_SERVER_ERROR,
          ErrorCode.CONFIG_INCOMPATIBLE.code(),
          "Token issuance is blocked due to incompatible active config set.");
    });
  }
}