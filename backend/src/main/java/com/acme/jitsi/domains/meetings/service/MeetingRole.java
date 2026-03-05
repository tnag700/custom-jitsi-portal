package com.acme.jitsi.domains.meetings.service;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;
import java.util.Optional;

public enum MeetingRole {
  PARTICIPANT("participant"),
  MODERATOR("moderator"),
  HOST("host");

  public final String value;

  MeetingRole(String value) {
    this.value = value;
  }

  @JsonValue
  public String value() {
    return value;
  }

  @JsonCreator
  public static MeetingRole fromValue(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }

    String normalized = value.trim().toLowerCase(Locale.ROOT);
    for (MeetingRole role : values()) {
      if (role.value.equals(normalized)) {
        return role;
      }
    }

    throw new IllegalArgumentException("Unknown MeetingRole: " + value);
  }

  public static Optional<MeetingRole> from(String value) {
    if (value == null || value.isBlank()) {
      return Optional.empty();
    }

    String normalized = value.trim().toLowerCase(Locale.ROOT);
    for (MeetingRole role : values()) {
      if (role.value.equals(normalized)) {
        return Optional.of(role);
      }
    }

    return Optional.empty();
  }
}
