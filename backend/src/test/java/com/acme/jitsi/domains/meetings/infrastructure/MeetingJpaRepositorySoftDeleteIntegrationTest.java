package com.acme.jitsi.domains.meetings.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.acme.jitsi.shared.JwtTestProperties;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:testdb;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
      "spring.datasource.driver-class-name=org.h2.Driver",
      "spring.jpa.hibernate.ddl-auto=validate",
      "spring.flyway.enabled=true",
      "management.health.redis.enabled=false",
      "app.security.sso.expected-issuer=https://issuer.example.test",
      JwtTestProperties.TOKEN_SIGNING_SECRET,
      JwtTestProperties.TOKEN_ISSUER,
      JwtTestProperties.TOKEN_AUDIENCE,
      JwtTestProperties.TOKEN_ALGORITHM,
      JwtTestProperties.TOKEN_TTL_MINUTES,
      JwtTestProperties.TOKEN_ROLE_CLAIM_NAME,
      "app.auth.refresh.idle-ttl-minutes=60",
      JwtTestProperties.CONTOUR_ISSUER,
      JwtTestProperties.CONTOUR_AUDIENCE,
      JwtTestProperties.CONTOUR_ROLE_CLAIM,
      JwtTestProperties.CONTOUR_ALGORITHM,
      JwtTestProperties.CONTOUR_ACCESS_TTL_MINUTES,
      JwtTestProperties.CONTOUR_REFRESH_TTL_MINUTES,
      "app.rooms.valid-config-sets=config-1,config-2",
      "app.rooms.config-sets.config-1.issuer=https://portal.example.test",
      "app.rooms.config-sets.config-1.audience=jitsi-meet",
      "app.rooms.config-sets.config-1.role-claim=role",
      "app.rooms.config-sets.config-2.issuer=https://portal.example.test",
      "app.rooms.config-sets.config-2.audience=jitsi-meet",
      "app.rooms.config-sets.config-2.role-claim=role",
    })
class MeetingJpaRepositorySoftDeleteIntegrationTest {

  @Autowired
  private MeetingJpaRepository meetingJpaRepository;

  @Autowired
  private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void setUp() {
    jdbcTemplate.execute("DELETE FROM meeting_audit_events");
    jdbcTemplate.execute("DELETE FROM meeting_invites");
    jdbcTemplate.execute("DELETE FROM meetings");
    jdbcTemplate.execute("DELETE FROM rooms");
  }

  @Test
  void roomQueriesIgnoreDeletedMeetings() {
    insertRoom("room-m-1");
    Instant now = Instant.parse("2026-02-23T10:00:00Z");

    insertMeeting("meeting-active-1", "room-m-1", false, now.plusSeconds(3600), now.plusSeconds(7200));
    insertMeeting("meeting-deleted-1", "room-m-1", true, now.plusSeconds(3600), now.plusSeconds(7200));

    assertThat(meetingJpaRepository.findById("meeting-active-1")).isPresent();
    assertThat(meetingJpaRepository.findById("meeting-deleted-1")).isEmpty();

    assertThat(meetingJpaRepository.findByRoomIdOrderByCreatedAtDesc("room-m-1", PageRequest.of(0, 10)).getContent())
        .extracting(MeetingEntity::toDomain)
        .extracting(meeting -> meeting.meetingId())
        .containsExactly("meeting-active-1");

    assertThat(meetingJpaRepository.existsActiveOrFutureMeetings("room-m-1", now.minusSeconds(3600), now)).isTrue();
  }

  @Test
  void existsActiveOrFutureMeetingsReturnsFalseWhenOnlyDeletedMeetingsExist() {
    insertRoom("room-m-2");
    Instant now = Instant.parse("2026-02-23T10:00:00Z");

    insertMeeting("meeting-deleted-only", "room-m-2", true, now.plusSeconds(3600), now.plusSeconds(7200));

    assertThat(meetingJpaRepository.existsActiveOrFutureMeetings("room-m-2", now.minusSeconds(3600), now)).isFalse();
  }

  private void insertRoom(String roomId) {
    Instant now = Instant.parse("2026-02-23T10:00:00Z");
    jdbcTemplate.update(
        """
        INSERT INTO rooms (room_id, name, description, tenant_id, config_set_id, status, created_at, updated_at, deleted)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        roomId,
        "Room " + roomId,
        null,
        "tenant-1",
        "config-1",
        "ACTIVE",
        now,
        now,
        false);
  }

  private void insertMeeting(String meetingId, String roomId, boolean deleted, Instant startsAt, Instant endsAt) {
    Instant now = Instant.parse("2026-02-23T10:00:00Z");
    jdbcTemplate.update(
        """
        INSERT INTO meetings (
          meeting_id, room_id, title, description, meeting_type, config_set_id, status,
          starts_at, ends_at, allow_guests, recording_enabled, created_at, updated_at, deleted
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        meetingId,
        roomId,
        "Meeting " + meetingId,
        null,
        "scheduled",
        "config-1",
        "SCHEDULED",
        startsAt,
        endsAt,
        true,
        false,
        now,
        now,
        deleted);
  }
}