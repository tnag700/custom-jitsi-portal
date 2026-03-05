package com.acme.jitsi.domains.meetings.service;

import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.stereotype.Component;

@Component
public class SecureInviteTokenGenerator {

  private static final int TOKEN_BYTES = 32; // 256 bits
  private final SecureRandom secureRandom;

  public SecureInviteTokenGenerator() {
    this.secureRandom = new SecureRandom();
  }

  public String generateToken() {
    byte[] bytes = new byte[TOKEN_BYTES];
    secureRandom.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }
}
