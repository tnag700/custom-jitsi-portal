package com.acme.jitsi.domains.rooms.usecase;

import com.acme.jitsi.domains.rooms.event.RoomDeletedEvent;
import com.acme.jitsi.domains.rooms.service.ActiveMeetingsChecker;
import com.acme.jitsi.domains.rooms.service.Room;
import com.acme.jitsi.domains.rooms.service.RoomHasActiveMeetingsException;
import com.acme.jitsi.domains.rooms.service.RoomRepository;
import com.acme.jitsi.infrastructure.usecase.UseCase;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeleteRoomUseCase implements UseCase<DeleteRoomCommand, Void> {

  private final RoomRepository roomRepository;
  private final ActiveMeetingsChecker activeMeetingsChecker;
  private final ApplicationEventPublisher eventPublisher;

  public DeleteRoomUseCase(
      RoomRepository roomRepository,
      ActiveMeetingsChecker activeMeetingsChecker,
      ApplicationEventPublisher eventPublisher) {
    this.roomRepository = roomRepository;
    this.activeMeetingsChecker = activeMeetingsChecker;
    this.eventPublisher = eventPublisher;
  }

  @Override
  @Transactional
  public Void execute(DeleteRoomCommand command) {
    Room existing = command.existing();
    assertNoActiveOrFutureMeetings(existing.roomId());
    roomRepository.deleteById(existing.roomId());

    eventPublisher.publishEvent(new RoomDeletedEvent(
        existing.roomId(),
        command.actorId(),
        command.traceId(),
        "name,tenantId,configSetId,status",
        "name=%s,tenantId=%s,configSetId=%s,status=%s"
            .formatted(existing.name(), existing.tenantId(), existing.configSetId(), existing.status()),
        "-"));
    return null;
  }

  private void assertNoActiveOrFutureMeetings(String roomId) {
    if (activeMeetingsChecker.hasActiveOrFutureMeetings(roomId)) {
      throw new RoomHasActiveMeetingsException(roomId);
    }
  }
}
