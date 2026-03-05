package com.acme.jitsi.domains.rooms.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.junit.jupiter.api.Test;

class RoomEntitySoftDeleteAnnotationsTest {

  @Test
  void roomEntityHasSoftDeleteAnnotations() {
    SQLDelete sqlDelete = RoomEntity.class.getAnnotation(SQLDelete.class);
    SQLRestriction sqlRestriction = RoomEntity.class.getAnnotation(SQLRestriction.class);

    assertThat(sqlDelete).isNotNull();
    assertThat(sqlDelete.sql()).isEqualTo("UPDATE rooms SET deleted = true WHERE room_id = ?");
    assertThat(sqlRestriction).isNotNull();
    assertThat(sqlRestriction.value()).isEqualTo("deleted = false");
  }
}