package com.acme.jitsi.domains.rooms.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.acme.jitsi.shared.TestFixtures;
import com.acme.jitsi.domains.rooms.event.RoomUpdatedEvent;
import com.acme.jitsi.domains.rooms.service.ConfigSetValidator;
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
class UpdateRoomUseCaseTest {

  @Mock
  private RoomRepository roomRepository;
  @Mock
  private ConfigSetValidator configSetValidator;
  @Mock
  private ApplicationEventPublisher eventPublisher;

  private UpdateRoomUseCase useCase;

  @BeforeEach
  void setUp() {
    useCase = new UpdateRoomUseCase(
        roomRepository,
        configSetValidator,
        eventPublisher,
        Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
  }

  @Test
  void executeUpdatesRoomAndPublishesEvent() {
    Room existing = TestFixtures.room("room-1", "Old", "desc", "tenant-1", "config-1", RoomStatus.ACTIVE);
    when(roomRepository.existsByNameAndTenantIdAndRoomIdNot("New", "tenant-1", "room-1")).thenReturn(false);
    when(roomRepository.save(any(Room.class))).thenAnswer(invocation -> invocation.getArgument(0));

    Room updated = useCase.execute(new UpdateRoomCommand(existing, "New", null, "config-1", "actor-1", "trace-1"));

    assertThat(updated.name()).isEqualTo("New");
    verify(eventPublisher).publishEvent(any(RoomUpdatedEvent.class));
  }
}
