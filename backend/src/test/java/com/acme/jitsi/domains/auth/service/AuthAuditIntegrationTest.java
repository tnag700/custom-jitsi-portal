package com.acme.jitsi.domains.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.acme.jitsi.shared.JwtTestProperties;
import java.time.Instant;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:testdb-auth-audit;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
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
      JwtTestProperties.CONTOUR_REFRESH_TTL_MINUTES
    })
class AuthAuditIntegrationTest {

  @Autowired
  private ApplicationEventPublisher applicationEventPublisher;

  @Autowired
  private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void setUp() {
    jdbcTemplate.execute("DELETE FROM auth_audit_events");
  }

  @Test
  void refreshSecurityEventIsPersistedIntoDedicatedAuthAuditStream() {
    applicationEventPublisher.publishEvent(new AuthRefreshSecurityEvent(
        "TOKEN_REFRESHED",
        null,
        "token-1",
        "u-host",
        "meeting-a",
      "trace-auth-audit-1",
        Instant.parse("2026-03-16T10:15:30Z")));

    Integer count = awaitCount(
        """
            SELECT COUNT(*)
            FROM auth_audit_events
            WHERE event_type = ?
              AND subject_id = ?
              AND meeting_id = ?
        """,
        "TOKEN_REFRESHED",
        "u-host",
        "meeting-a");

    assertThat(count).isEqualTo(1);
  }

  private Integer awaitCount(String sql, Object... args) {
    long deadline = System.nanoTime() + Duration.ofSeconds(3).toNanos();
    Integer count = 0;
    while (System.nanoTime() < deadline) {
      count = jdbcTemplate.queryForObject(sql, Integer.class, args);
      if (count != null && count > 0) {
        return count;
      }
      try {
        Thread.sleep(50);
      } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
        throw new AssertionError("Interrupted while waiting for auth audit event", ex);
      }
    }
    return count;
  }
}