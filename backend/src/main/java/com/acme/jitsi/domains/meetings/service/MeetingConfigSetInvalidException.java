package com.acme.jitsi.domains.meetings.service;

public class MeetingConfigSetInvalidException extends RuntimeException {

  public MeetingConfigSetInvalidException(String configSetId) {
    super("Invalid config set id: " + configSetId);
  }
}