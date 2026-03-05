package com.acme.jitsi.domains.profiles.service;

public class ProfileNotFoundException extends RuntimeException {
  public ProfileNotFoundException(String subjectId) {
    super("Profile not found for subject '" + subjectId + "'");
  }
}
