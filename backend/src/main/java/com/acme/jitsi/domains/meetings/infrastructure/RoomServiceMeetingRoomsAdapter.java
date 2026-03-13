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
    Room room;
    try {
      room = roomService.getRoom(roomId);
    } catch (RoomNotFoundException ex) {
      throw new MeetingRoomNotFoundException(roomId);
    }
    return new MeetingRoomSnapshot(
        room.roomId(),
        room.name(),
        room.tenantId(),
        room.configSetId(),
        room.status() == RoomStatus.ACTIVE,
        configSetValidator.isValid(room.configSetId()));
  }
}