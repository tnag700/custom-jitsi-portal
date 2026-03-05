package com.acme.jitsi.domains.rooms.usecase;

import com.acme.jitsi.domains.rooms.event.RoomClosedEvent;
import com.acme.jitsi.domains.rooms.service.ActiveMeetingsChecker;
import com.acme.jitsi.domains.rooms.service.Room;
import com.acme.jitsi.domains.rooms.service.RoomAlreadyClosedException;
import com.acme.jitsi.domains.rooms.service.RoomHasActiveMeetingsException;
import com.acme.jitsi.domains.rooms.service.RoomRepository;
import com.acme.jitsi.domains.rooms.service.RoomStatus;
import com.acme.jitsi.infrastructure.usecase.UseCase;
import java.time.Clock;
import java.time.Instant;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CloseRoomUseCase implements UseCase<CloseRoomCommand, Room> {

  private final RoomRepository roomRepository;
  private final ActiveMeetingsChecker activeMeetingsChecker;
  private final ApplicationEventPublisher eventPublisher;
  private final Clock clock;

  public CloseRoomUseCase(
      RoomRepository roomRepository,
      ActiveMeetingsChecker activeMeetingsChecker,
      ApplicationEventPublisher eventPublisher,
      Clock clock) {
    this.roomRepository = roomRepository;
    this.activeMeetingsChecker = activeMeetingsChecker;
    this.eventPublisher = eventPublisher;
    this.clock = clock;
  }

  @Override
  @Transactional
  public Room execute(CloseRoomCommand command) {
    Room existing = command.existing();

    if (existing.status() == RoomStatus.CLOSED) {
      throw new RoomAlreadyClosedException(existing.roomId());
    }

    assertNoActiveOrFutureMeetings(existing.roomId());

    Instant now = Instant.now(clock);
    Room closed = new Room(
        existing.roomId(),
        existing.name(),
        existing.description(),
        existing.tenantId(),
        existing.configSetId(),
        RoomStatus.CLOSED,
        existing.createdAt(),
        now);

    Room saved = roomRepository.save(closed);

    eventPublisher.publishEvent(new RoomClosedEvent(
        saved.roomId(),
        command.actorId(),
        command.traceId(),
        "status",
        "status=%s".formatted(existing.status()),
        "status=%s".formatted(saved.status())));

    return saved;
  }

  private void assertNoActiveOrFutureMeetings(String roomId) {
    if (activeMeetingsChecker.hasActiveOrFutureMeetings(roomId)) {
      throw new RoomHasActiveMeetingsException(roomId);
    }
  }
}
