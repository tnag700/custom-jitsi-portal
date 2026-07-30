package com.acme.jitsi.domains.profiles.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.acme.jitsi.shared.ErrorCode;
import com.acme.jitsi.shared.JwtTestProperties;
import java.sql.Timestamp;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.OAuth2LoginRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:testdb-admin-profiles;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
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
      "app.rooms.valid-config-sets=config-1",
      "app.rooms.config-sets.config-1.issuer=https://portal.example.test",
      "app.rooms.config-sets.config-1.audience=jitsi-meet",
      "app.rooms.config-sets.config-1.role-claim=role",
    })
@AutoConfigureMockMvc
class AdminUserProfilesControllerTest {

  @Autowired
  private MockMvc mockMvc;
  @Autowired
  private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void setUp() {
    jdbcTemplate.execute("DELETE FROM user_profiles");
    insertProfile("profile-1", "user-1", "tenant-1", "Иван Иванов");
    insertProfile("profile-2", "user-2", "tenant-2", "Пётр Петров");
  }

  @Test
  void adminSearchReturnsOnlyProfilesFromOwnTenant() throws Exception {
    mockMvc.perform(get("/api/v1/admin/users").with(login("admin-1", "tenant-1", "ROLE_admin")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].subjectId").value("user-1"))
        .andExpect(jsonPath("$[0].tenantId").value("tenant-1"));
  }

  @Test
  void adminCanUpdateAnotherUsersProfile() throws Exception {
    mockMvc.perform(put("/api/v1/admin/users/user-1")
            .with(csrf())
            .with(login("admin-1", "tenant-1", "ROLE_admin"))
            .header("X-Trace-Id", "trace-admin-update")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "fullName": "Иван Иванович Иванов",
                  "organization": "Новая клиника",
                  "position": "Главный врач"
                }
                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.subjectId").value("user-1"))
        .andExpect(jsonPath("$.tenantId").value("tenant-1"))
        .andExpect(jsonPath("$.fullName").value("Иван Иванович Иванов"))
        .andExpect(jsonPath("$.organization").value("Новая клиника"))
        .andExpect(jsonPath("$.position").value("Главный врач"));
  }

  @Test
  void adminCannotUpdateProfileFromAnotherTenant() throws Exception {
    mockMvc.perform(put("/api/v1/admin/users/user-2")
            .with(csrf())
            .with(login("admin-1", "tenant-1", "ROLE_admin"))
            .contentType(MediaType.APPLICATION_JSON)
            .content(validBody()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.properties.errorCode").value(ErrorCode.PROFILE_NOT_FOUND.code()));
  }

  @Test
  void nonAdminCannotReadOrUpdateProfiles() throws Exception {
    var participant = login("participant-1", "tenant-1", "ROLE_participant");
    mockMvc.perform(get("/api/v1/admin/users").with(participant))
        .andExpect(status().isForbidden());

    mockMvc.perform(put("/api/v1/admin/users/user-1")
            .with(csrf())
            .with(login("support-1", "tenant-1", "ROLE_support-engineer"))
            .contentType(MediaType.APPLICATION_JSON)
            .content(validBody()))
        .andExpect(status().isForbidden());
  }

  private OAuth2LoginRequestPostProcessor login(String subjectId, String tenantId, String role) {
    return oauth2Login()
        .attributes(attributes -> {
          attributes.put("sub", subjectId);
          attributes.put("tenantId", tenantId);
        })
        .authorities(new SimpleGrantedAuthority(role));
  }

  private void insertProfile(String id, String subjectId, String tenantId, String fullName) {
    Timestamp now = Timestamp.from(Instant.parse("2026-07-30T00:00:00Z"));
    jdbcTemplate.update(
        """
        INSERT INTO user_profiles
          (id, subject_id, tenant_id, full_name, organization, position, created_at, updated_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        """,
        id,
        subjectId,
        tenantId,
        fullName,
        "Клиника",
        "Врач",
        now,
        now);
  }

  private String validBody() {
    return """
        {
          "fullName": "Новое Имя",
          "organization": "Новая клиника",
          "position": "Новая должность"
        }
        """;
  }
}
