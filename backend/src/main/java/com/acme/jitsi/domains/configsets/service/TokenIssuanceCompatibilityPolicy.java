package com.acme.jitsi.domains.configsets.service;

import com.acme.jitsi.security.TokenIssuancePolicyException;
import com.acme.jitsi.shared.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
@org.springframework.modulith.NamedInterface(value = "service", propagate = false)
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
