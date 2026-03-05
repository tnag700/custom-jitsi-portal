package com.acme.jitsi.domains.meetings.service;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum JoinAvailability {
  AVAILABLE("available"),
  SCHEDULED("scheduled");

  private final String value;

  JoinAvailability(String value) {
    this.value = value;
  }

  @JsonValue
  public String value() {
    return value;
  }

  @JsonCreator
  public static JoinAvailability fromValue(String value) {
    for (JoinAvailability candidate : values()) {
      if (candidate.value.equals(value)) {
        return candidate;
      }
    }
    throw new IllegalArgumentException("Unknown joinAvailability: " + value);
  }
}