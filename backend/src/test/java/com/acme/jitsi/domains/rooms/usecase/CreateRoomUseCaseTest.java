package com.acme.jitsi.domains.rooms.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.acme.jitsi.domains.rooms.event.RoomCreatedEvent;
import com.acme.jitsi.domains.rooms.service.ConfigSetValidator;
import com.acme.jitsi.domains.rooms.service.Room;
import com.acme.jitsi.domains.rooms.service.RoomNameConflictException;
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
class CreateRoomUseCaseTest {

  @Mock
  private RoomRepository roomRepository;
  @Mock
  private ConfigSetValidator configSetValidator;
  @Mock
  private ApplicationEventPublisher eventPublisher;

  private CreateRoomUseCase useCase;

  @BeforeEach
  void setUp() {
    useCase = new CreateRoomUseCase(
        roomRepository,
        configSetValidator,
        eventPublisher,
        Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
  }

  @Test
  void executeCreatesRoomAndPublishesEvent() {
    when(configSetValidator.isValid("config-1")).thenReturn(true);
    when(roomRepository.existsByNameAndTenantId("Room A", "tenant-1")).thenReturn(false);
    when(roomRepository.save(any(Room.class))).thenAnswer(invocation -> invocation.getArgument(0));

    Room room = useCase.execute(new CreateRoomCommand("Room A", "desc", "tenant-1", "config-1", "actor-1", "trace-1"));

    assertThat(room.status()).isEqualTo(RoomStatus.ACTIVE);
    verify(eventPublisher).publishEvent(any(RoomCreatedEvent.class));
  }

  @Test
  void executeThrowsWhenRoomNameAlreadyExists() {
    when(configSetValidator.isValid("config-1")).thenReturn(true);
    when(roomRepository.existsByNameAndTenantId("Room A", "tenant-1")).thenReturn(true);

    assertThatThrownBy(() -> useCase.execute(new CreateRoomCommand("Room A", "desc", "tenant-1", "config-1", "actor-1", "trace-1")))
        .isInstanceOf(RoomNameConflictException.class);
  }
}
