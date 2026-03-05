package com.acme.jitsi.domains.rooms.service;

public interface ActiveMeetingsChecker {
  boolean hasActiveOrFutureMeetings(String roomId);
}
