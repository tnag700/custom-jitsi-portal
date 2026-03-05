package com.acme.jitsi.domains.rooms.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.acme.jitsi.domains.rooms.event.RoomClosedEvent;
import com.acme.jitsi.domains.rooms.service.ActiveMeetingsChecker;
import com.acme.jitsi.domains.rooms.service.Room;
import com.acme.jitsi.domains.rooms.service.RoomRepository;
import com.acme.jitsi.domains.rooms.service.RoomStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class CloseRoomUseCaseTest {

  @Mock
  private RoomRepository roomRepository;
  @Mock
  private ActiveMeetingsChecker activeMeetingsChecker;
  @Mock
  private ApplicationEventPublisher eventPublisher;

  private CloseRoomUseCase useCase;

  @BeforeEach
  void setUp() {
    useCase = new CloseRoomUseCase(
        roomRepository,
        activeMeetingsChecker,
        eventPublisher,
        Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
  }

  @Test
  void executeClosesRoomAndPublishesEvent() {
    Room existing = new Room("room-1", "Room", null, "tenant-1", "config-1", RoomStatus.ACTIVE, Instant.now(), Instant.now());
    when(activeMeetingsChecker.hasActiveOrFutureMeetings("room-1")).thenReturn(false);
    when(roomRepository.save(any(Room.class))).thenAnswer(invocation -> invocation.getArgument(0));

    Room closed = useCase.execute(new CloseRoomCommand(existing, "actor-1", "trace-1"));

    assertThat(closed.status()).isEqualTo(RoomStatus.CLOSED);
    verify(eventPublisher).publishEvent(any(RoomClosedEvent.class));
  }
}
