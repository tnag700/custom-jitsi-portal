package com.acme.jitsi.domains.profiles.service;

public class ProfileValidationException extends RuntimeException {
  public ProfileValidationException(String message) {
    super(message);
  }
}
