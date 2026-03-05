package com.acme.jitsi.domains.rooms.infrastructure;

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
class RoomJpaRepositorySoftDeleteIntegrationTest {

  @Autowired
  private RoomJpaRepository roomJpaRepository;

  @Autowired
  private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void setUp() {
    jdbcTemplate.execute("DELETE FROM meetings");
    jdbcTemplate.execute("DELETE FROM rooms");
  }

  @Test
  void deleteByIdPerformsSoftDelete() {
    insertRoom("room-soft-1", "Room Soft", "tenant-1", false);

    roomJpaRepository.deleteById("room-soft-1");

    Boolean deletedFlag = jdbcTemplate.queryForObject(
        "SELECT deleted FROM rooms WHERE room_id = ?", Boolean.class, "room-soft-1");
    Integer rowCount = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM rooms WHERE room_id = ?", Integer.class, "room-soft-1");

    assertThat(deletedFlag).isTrue();
    assertThat(rowCount).isEqualTo(1);
  }

  @Test
  void repositoryQueriesFilterDeletedRowsAutomatically() {
    insertRoom("room-active-1", "Active Room", "tenant-1", false);
    insertRoom("room-deleted-1", "Deleted Room", "tenant-1", true);

    assertThat(roomJpaRepository.findById("room-active-1")).isPresent();
    assertThat(roomJpaRepository.findById("room-deleted-1")).isEmpty();

    assertThat(roomJpaRepository.findByTenantIdOrderByCreatedAtDesc("tenant-1", PageRequest.of(0, 10)).getContent())
        .extracting(RoomEntity::getRoomId)
        .containsExactly("room-active-1");

    assertThat(roomJpaRepository.countByTenantId("tenant-1")).isEqualTo(1);
    assertThat(roomJpaRepository.existsByNameAndTenantId("Active Room", "tenant-1")).isTrue();
    assertThat(roomJpaRepository.existsByNameAndTenantId("Deleted Room", "tenant-1")).isFalse();
  }

  private void insertRoom(String roomId, String name, String tenantId, boolean deleted) {
    Instant now = Instant.parse("2026-02-23T10:00:00Z");
    jdbcTemplate.update(
        """
        INSERT INTO rooms (room_id, name, description, tenant_id, config_set_id, status, created_at, updated_at, deleted)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        roomId,
        name,
        null,
        tenantId,
        "config-1",
        "ACTIVE",
        now,
        now,
        deleted);
  }
}