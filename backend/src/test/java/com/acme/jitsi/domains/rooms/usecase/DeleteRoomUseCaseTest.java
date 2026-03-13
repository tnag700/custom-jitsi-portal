package com.acme.jitsi.domains.rooms.usecase;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.acme.jitsi.shared.TestFixtures;
import com.acme.jitsi.domains.rooms.event.RoomDeletedEvent;
import com.acme.jitsi.domains.rooms.service.ActiveMeetingsChecker;
import com.acme.jitsi.domains.rooms.service.Room;
import com.acme.jitsi.domains.rooms.service.RoomRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class DeleteRoomUseCaseTest {

  @Mock
  private RoomRepository roomRepository;
  @Mock
  private ActiveMeetingsChecker activeMeetingsChecker;
  @Mock
  private ApplicationEventPublisher eventPublisher;

  private DeleteRoomUseCase useCase;

  @BeforeEach
  void setUp() {
    useCase = new DeleteRoomUseCase(roomRepository, activeMeetingsChecker, eventPublisher);
  }

  @Test
  void executeDeletesRoomAndPublishesEvent() {
    Room existing = TestFixtures.room();
    when(activeMeetingsChecker.hasActiveOrFutureMeetings("room-1")).thenReturn(false);

    useCase.execute(new DeleteRoomCommand(existing, "actor-1", "trace-1"));

    verify(roomRepository).deleteById("room-1");
    verify(eventPublisher).publishEvent(any(RoomDeletedEvent.class));
  }
}
