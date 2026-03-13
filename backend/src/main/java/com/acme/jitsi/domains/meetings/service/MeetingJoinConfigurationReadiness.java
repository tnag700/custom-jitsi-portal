package com.acme.jitsi.domains.meetings.service;

import java.util.List;

public record MeetingJoinConfigurationReadiness(
    String publicJoinUrl,
    List<ConfigurationCheck> checks) {

  public record ConfigurationCheck(
      String key,
      String status,
      String headline,
      String reason,
      List<String> actions,
      String errorCode,
      boolean blocking) {
  }
}