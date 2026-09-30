package com.acme.jitsi.domains.meetings.infrastructure;

import com.acme.jitsi.domains.meetings.service.MeetingRoomSnapshot;
import com.acme.jitsi.domains.meetings.service.MeetingRoomNotFoundException;
import com.acme.jitsi.domains.meetings.service.MeetingRoomsPort;
import com.acme.jitsi.domains.rooms.service.ConfigSetValidator;
import com.acme.jitsi.domains.rooms.service.Room;
import com.acme.jitsi.domains.rooms.service.RoomNotFoundException;
import com.acme.jitsi.domains.rooms.service.RoomService;
import com.acme.jitsi.domains.rooms.service.RoomStatus;
import org.springframework.stereotype.Component;
import java.util.Map;
import java.util.Set;

@Component
public class RoomServiceMeetingRoomsAdapter implements MeetingRoomsPort {

  private final RoomService roomService;
  private final ConfigSetValidator configSetValidator;

  public RoomServiceMeetingRoomsAdapter(
      RoomService roomService,
      ConfigSetValidator configSetValidator) {
    this.roomService = roomService;
    this.configSetValidator = configSetValidator;
  }

  @Override
  public MeetingRoomSnapshot getRequiredRoom(String roomId) {
    return getRoom(roomId, false);
  }

  @Override
  public MeetingRoomSnapshot getRequiredRoomForUpdate(String roomId) {
    return getRoom(roomId, true);
  }

  @Override
  public boolean isConfigSetValid(MeetingRoomSnapshot room) {
    return configSetValidator.isValid(room.configSetId(), room.tenantId());
  }

  @Override
  public Map<String, String> getRoomNames(Set<String> roomIds) {
    return roomService.getRoomNames(roomIds);
  }

  private MeetingRoomSnapshot getRoom(String roomId, boolean forUpdate) {
    Room room;
    try {
      room = forUpdate ? roomService.getRoomForUpdate(roomId) : roomService.getRoom(roomId);
    } catch (RoomNotFoundException ex) {
      throw new MeetingRoomNotFoundException(roomId, ex);
    }
    return new MeetingRoomSnapshot(
        room.roomId(),
        room.name(),
        room.tenantId(),
        room.configSetId(),
        room.status() == RoomStatus.ACTIVE);
  }
}
