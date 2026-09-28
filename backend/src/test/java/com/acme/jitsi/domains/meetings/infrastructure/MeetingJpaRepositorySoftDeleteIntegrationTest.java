package com.acme.jitsi.domains.meetings.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.acme.jitsi.domains.meetings.service.Meeting;
import com.acme.jitsi.domains.meetings.service.MeetingFinalizedException;
import com.acme.jitsi.domains.meetings.service.MeetingStatus;
import com.acme.jitsi.domains.meetings.usecase.CancelMeetingCommand;
import com.acme.jitsi.domains.meetings.usecase.CancelMeetingUseCase;
import com.acme.jitsi.domains.meetings.usecase.UpdateMeetingCommand;
import com.acme.jitsi.domains.meetings.usecase.UpdateMeetingUseCase;
import com.acme.jitsi.domains.meetings.service.UpcomingMeetingsReader;
import com.acme.jitsi.domains.meetings.service.UpcomingMeetingCard;
import com.acme.jitsi.domains.meetings.service.MeetingRoomInactiveException;
import com.acme.jitsi.domains.meetings.usecase.CreateMeetingCommand;
import com.acme.jitsi.domains.meetings.usecase.CreateMeetingUseCase;
import com.acme.jitsi.domains.rooms.service.RoomService;
import com.acme.jitsi.domains.rooms.usecase.CloseRoomCommand;
import com.acme.jitsi.domains.rooms.usecase.CloseRoomUseCase;
import jakarta.persistence.EntityManagerFactory;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
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
      "spring.jpa.properties.hibernate.generate_statistics=true",
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
  private JpaMeetingRepository repository;

  @Autowired
  private MeetingJpaRepository meetingJpaRepository;

  @Autowired
  private JdbcTemplate jdbcTemplate;

  @Autowired
  private UpdateMeetingUseCase updateMeeting;

  @Autowired
  private CancelMeetingUseCase cancelMeeting;

  @Autowired private UpcomingMeetingsReader upcoming;
  @Autowired private EntityManagerFactory entityManagerFactory;
  @Autowired private PlatformTransactionManager transactionManager;
  @Autowired private CreateMeetingUseCase createMeeting;
  @Autowired private CloseRoomUseCase closeRoom;
  @Autowired private RoomService roomService;

  @Test
  void upcomingFiltersHistoryAndLoadsOneHundredCardsWithTwoQueries() {
    insertRoom("room-upcoming");
    Instant now = Instant.now();
    for (int i = 0; i < 104; i++) {
      String id = "upcoming-" + i;
      boolean history = i == 100;
      insertMeeting(id, "room-upcoming", i == 102,
          history ? now.minusSeconds(7200) : now.plusSeconds(3600 + i),
          history ? now.minusSeconds(3600) : now.plusSeconds(7200 + i));
      jdbcTemplate.update("""
          INSERT INTO meeting_participant_assignments
          (assignment_id, meeting_id, subject_id, role, assigned_at, assigned_by, created_at, updated_at)
          VALUES (?, ?, ?, 'participant', ?, 'admin', ?, ?)
          """, "assignment-" + i, id, i == 103 ? "other-user" : "user-upcoming", now, now, now);
    }
    jdbcTemplate.update("UPDATE meetings SET status = 'CANCELED' WHERE meeting_id = 'upcoming-101'");
    var statistics = entityManagerFactory.unwrap(org.hibernate.SessionFactory.class).getStatistics();
    statistics.clear();

    var cards = upcoming.listForSubject("user-upcoming");

    assertThat(cards).hasSize(100).extracting(UpcomingMeetingCard::roomName).containsOnly("Room room-upcoming");
    assertThat(cards).extracting(UpcomingMeetingCard::startsAt).isSorted();
    assertThat(statistics.getPrepareStatementCount()).isEqualTo(2);
  }

  @Test
  void updateRefreshesEntityAlreadyReadByTheControllerTransaction() {
    insertRoom("room-stale");
    Instant now = Instant.now();
    insertMeeting("meeting-stale", "room-stale", false, now.plusSeconds(3600), now.plusSeconds(7200));
    TransactionTemplate transaction = new TransactionTemplate(transactionManager);

    assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
      Meeting stale = repository.findById("meeting-stale").orElseThrow();
      CompletableFuture.runAsync(() -> cancelMeeting.execute(new CancelMeetingCommand(stale, "actor", "trace"))).join();
      updateMeeting.execute(new UpdateMeetingCommand(
          stale, "New title", null, null, null, null, null, null, "actor", "trace"));
    })).isInstanceOf(MeetingFinalizedException.class);
    assertThat(repository.findById("meeting-stale").orElseThrow().status()).isEqualTo(MeetingStatus.CANCELED);
  }

  @Test
  void createWaitsForConcurrentRoomCloseAndRejectsClosedRoom() throws Exception {
    insertRoom("room-close");
    var room = roomService.getRoom("room-close");
    var attempted = new CountDownLatch(1);
    var creation = new java.util.concurrent.atomic.AtomicReference<CompletableFuture<Meeting>>();
    new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
      jdbcTemplate.queryForObject("SELECT room_id FROM rooms WHERE room_id = 'room-close' FOR UPDATE", String.class);
      creation.set(CompletableFuture.supplyAsync(() -> {
        attempted.countDown();
        Instant now = Instant.now();
        return createMeeting.execute(new CreateMeetingCommand("room-close", "Meeting", null, "scheduled",
            now.plusSeconds(3600), now.plusSeconds(7200), true, false, "actor", "trace"));
      }));
      try {
        assertThat(attempted.await(5, TimeUnit.SECONDS)).isTrue();
        assertThatThrownBy(() -> creation.get().get(150, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
      } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
        throw new AssertionError(ex);
      }
      closeRoom.execute(new CloseRoomCommand(room, "actor", "trace"));
    });
    assertThatThrownBy(() -> creation.get().get(5, TimeUnit.SECONDS)).hasCauseInstanceOf(MeetingRoomInactiveException.class);
    assertThat(repository.countByRoomId("room-close")).isZero();
  }

  @Test
  void staleUpdateCannotResurrectCanceledMeeting() {
    insertRoom("room-stale");
    Instant now = Instant.now();
    insertMeeting("meeting-stale", "room-stale", false, now.plusSeconds(3600), now.plusSeconds(7200));
    Meeting stale = repository.findById("meeting-stale").orElseThrow();
    cancelMeeting.execute(new CancelMeetingCommand(stale, "actor", "trace"));

    assertThatThrownBy(() -> updateMeeting.execute(new UpdateMeetingCommand(
        stale, "New title", null, null, null, null, null, null, "actor", "trace")))
        .isInstanceOf(MeetingFinalizedException.class);
    assertThat(repository.findById("meeting-stale").orElseThrow().status()).isEqualTo(MeetingStatus.CANCELED);
  }

  @Test
  void staleCancelPreservesConcurrentTitleChange() {
    insertRoom("room-stale");
    Instant now = Instant.now();
    insertMeeting("meeting-stale", "room-stale", false, now.plusSeconds(3600), now.plusSeconds(7200));
    Meeting stale = repository.findById("meeting-stale").orElseThrow();
    updateMeeting.execute(new UpdateMeetingCommand(
        stale, "New title", null, null, null, null, null, null, "actor", "trace"));

    Meeting canceled = cancelMeeting.execute(new CancelMeetingCommand(stale, "actor", "trace"));
    assertThat(canceled.title()).isEqualTo("New title");
    assertThat(canceled.status()).isEqualTo(MeetingStatus.CANCELED);
  }

  @BeforeEach
  void setUp() {
    jdbcTemplate.execute("DELETE FROM meeting_participant_assignments");
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

  @Test
  void adapterSaveRoundTripKeepsSingleRowAndUpdateSemantics() {
    insertRoom("room-m-3");
    Meeting original = new Meeting(
        "meeting-roundtrip-1",
        "room-m-3",
        "Planning",
        "Initial",
        "scheduled",
        "config-1",
        com.acme.jitsi.domains.meetings.service.MeetingStatus.SCHEDULED,
        Instant.parse("2026-02-23T11:00:00Z"),
        Instant.parse("2026-02-23T12:00:00Z"),
        true,
        false,
        Instant.parse("2026-02-23T10:00:00Z"),
        Instant.parse("2026-02-23T10:00:00Z"));
    Meeting updated = new Meeting(
        "meeting-roundtrip-1",
        "room-m-3",
        "Planning updated",
        "Updated",
        "scheduled",
        "config-1",
        com.acme.jitsi.domains.meetings.service.MeetingStatus.SCHEDULED,
        Instant.parse("2026-02-23T11:30:00Z"),
        Instant.parse("2026-02-23T12:30:00Z"),
        false,
        true,
        original.createdAt(),
        Instant.parse("2026-02-23T10:45:00Z"));

    assertThat(repository.save(original)).isEqualTo(original);
    assertThat(repository.save(updated)).isEqualTo(updated);
    assertThat(repository.findById(updated.meetingId())).contains(updated);
    assertThat(jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM meetings WHERE meeting_id = ?",
        Integer.class,
        updated.meetingId())).isEqualTo(1);
    assertThat(jdbcTemplate.queryForObject(
        "SELECT created_at FROM meetings WHERE meeting_id = ?",
        Instant.class,
        updated.meetingId())).isEqualTo(original.createdAt());
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
