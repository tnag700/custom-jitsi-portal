package com.acme.jitsi.domains.rooms.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.acme.jitsi.domains.rooms.service.Room;
import com.acme.jitsi.domains.rooms.service.RoomStatus;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JpaRoomRepositoryTest {

  @Mock
  private RoomJpaRepository jpaRepository;

  private JpaRoomRepository repository;

  @BeforeEach
  void setUp() {
    repository = new JpaRoomRepository(jpaRepository);
  }

  @Test
  void savePersistsDetachedEntityWithoutPreRead() {
    Room room = new Room(
        "room-1",
        "Room 1",
        "Daily sync",
        "tenant-1",
        "config-1",
        RoomStatus.ACTIVE,
        Instant.parse("2026-03-11T09:00:00Z"),
        Instant.parse("2026-03-11T09:30:00Z"));
    when(jpaRepository.save(any(RoomEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

    Room saved = repository.save(room);

    assertThat(saved).isEqualTo(room);
    verify(jpaRepository).save(any(RoomEntity.class));
    verify(jpaRepository, never()).findById(anyString());
  }
}