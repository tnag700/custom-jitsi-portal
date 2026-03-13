package com.acme.jitsi.domains.rooms.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.acme.jitsi.shared.TestFixtures;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RoomServiceTest {

  @Mock
  private RoomRepository roomRepository;

  private RoomService roomService;

  @BeforeEach
  void setUp() {
    roomService = new RoomService(roomRepository);
  }

  @Test
  void getRoomReturnsEntity() {
    Room room = TestFixtures.room();
    when(roomRepository.findById("room-1")).thenReturn(Optional.of(room));

    Room found = roomService.getRoom("room-1");
    assertThat(found.roomId()).isEqualTo("room-1");
  }

  @Test
  void getRoomThrowsWhenMissing() {
    when(roomRepository.findById("missing")).thenReturn(Optional.empty());
    assertThatThrownBy(() -> roomService.getRoom("missing"))
        .isInstanceOf(RoomNotFoundException.class);
  }

  @Test
  void listRoomsWithNonPositiveSizeThrowsException() {
    assertThatThrownBy(() -> roomService.listRooms("tenant-1", 0, 0))
        .isInstanceOf(InvalidRoomDataException.class)
        .hasMessageContaining("Size");
  }

  @Test
  void listRoomsWithNegativePageThrowsException() {
    assertThatThrownBy(() -> roomService.listRooms("tenant-1", -1, 20))
        .isInstanceOf(InvalidRoomDataException.class)
        .hasMessageContaining("Page");
  }

  @Test
  void listAndCountRoomsReturnRepositoryValues() {
    when(roomRepository.findByTenantId("tenant-1", 0, 20)).thenReturn(List.of());
    when(roomRepository.countByTenantId("tenant-1")).thenReturn(0L);

    assertThat(roomService.listRooms("tenant-1", 0, 20)).isEmpty();
    assertThat(roomService.countRooms("tenant-1")).isZero();
  }
}
