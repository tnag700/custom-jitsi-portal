package com.acme.jitsi.domains.rooms.infrastructure;

import com.acme.jitsi.domains.rooms.service.Room;
import com.acme.jitsi.domains.rooms.service.RoomStatus;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "rooms")
@SQLDelete(sql = "UPDATE rooms SET deleted = true WHERE room_id = ?")
@SQLRestriction("deleted = false")
class RoomEntity {

  @Id
  @Column(name = "room_id", nullable = false, updatable = false)
  private String roomId;

  @Column(name = "name", nullable = false)
  private String name;

  @Column(name = "description")
  private String description;

  @Column(name = "tenant_id", nullable = false)
  private String tenantId;

  @Column(name = "config_set_id", nullable = false)
  private String configSetId;

  @Convert(converter = RoomStatusConverter.class)
  @Column(name = "status", nullable = false)
  private RoomStatus status;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Column(name = "deleted", nullable = false)
  private boolean deleted = false;

  protected RoomEntity() {
    // JPA
  }

  RoomEntity(Room room) {
    this.roomId = room.roomId();
    this.name = room.name();
    this.description = room.description();
    this.tenantId = room.tenantId();
    this.configSetId = room.configSetId();
    this.status = room.status();
    this.createdAt = room.createdAt();
    this.updatedAt = room.updatedAt();
  }

  Room toDomain() {
    return new Room(roomId, name, description, tenantId, configSetId, status, createdAt, updatedAt);
  }

  void updateFrom(Room room) {
    this.name = room.name();
    this.description = room.description();
    this.configSetId = room.configSetId();
    this.status = room.status();
    this.updatedAt = room.updatedAt();
  }

  String getRoomId() {
    return roomId;
  }
}
