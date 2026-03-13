package com.acme.jitsi.domains.invites.service;

public interface InviteMeetingStatePort {

  void assertJoinAllowed(String meetingId);
}