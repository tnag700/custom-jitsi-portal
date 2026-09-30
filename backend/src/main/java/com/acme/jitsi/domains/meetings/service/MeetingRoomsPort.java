package com.acme.jitsi.domains.meetings.service;

import java.util.Map;
import java.util.Set;

public interface MeetingRoomsPort {

  MeetingRoomSnapshot getRequiredRoom(String roomId);

  MeetingRoomSnapshot getRequiredRoomForUpdate(String roomId);

  boolean isConfigSetValid(MeetingRoomSnapshot room);

  Map<String, String> getRoomNames(Set<String> roomIds);
}
