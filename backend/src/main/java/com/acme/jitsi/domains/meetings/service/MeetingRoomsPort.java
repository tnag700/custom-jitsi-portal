package com.acme.jitsi.domains.meetings.service;

public interface MeetingRoomsPort {

  MeetingRoomSnapshot getRequiredRoom(String roomId);
}