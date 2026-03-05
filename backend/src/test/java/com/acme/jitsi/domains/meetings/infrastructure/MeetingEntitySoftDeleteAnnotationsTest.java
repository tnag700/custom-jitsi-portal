package com.acme.jitsi.domains.meetings.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.junit.jupiter.api.Test;

class MeetingEntitySoftDeleteAnnotationsTest {

  @Test
  void meetingEntityHasSoftDeleteAnnotations() {
    SQLDelete sqlDelete = MeetingEntity.class.getAnnotation(SQLDelete.class);
    SQLRestriction sqlRestriction = MeetingEntity.class.getAnnotation(SQLRestriction.class);

    assertThat(sqlDelete).isNotNull();
    assertThat(sqlDelete.sql()).isEqualTo("UPDATE meetings SET deleted = true WHERE meeting_id = ?");
    assertThat(sqlRestriction).isNotNull();
    assertThat(sqlRestriction.value()).isEqualTo("deleted = false");
  }
}