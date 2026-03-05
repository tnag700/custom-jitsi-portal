package com.acme.jitsi.domains.profiles.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.acme.jitsi.shared.JwtTestProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:testdb-profiles;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
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
class ProfileControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void setUp() {
    jdbcTemplate.execute("DELETE FROM user_profiles");
  }

  // AC 1: GET /profile/me returns 404 when profile not filled
  @Test
  void getMyProfileReturns404WhenProfileNotExists() throws Exception {
    mockMvc.perform(get("/api/v1/profile/me")
            .with(oauth2Login()
                .attributes(attrs -> {
                  attrs.put("sub", "new-user");
                  attrs.put("tenantId", "tenant-1");
                })
                .authorities(new SimpleGrantedAuthority("ROLE_user"))))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.properties.errorCode").value("PROFILE_NOT_FOUND"))
        .andExpect(jsonPath("$.properties.traceId").exists());
  }

  // AC 2: PUT /profile/me creates profile, then GET returns it
  @Test
  void upsertAndGetMyProfile() throws Exception {
    mockMvc.perform(put("/api/v1/profile/me")
            .with(csrf())
            .with(oauth2Login()
                .attributes(attrs -> {
                  attrs.put("sub", "user-1");
                  attrs.put("tenantId", "tenant-1");
                })
                .authorities(new SimpleGrantedAuthority("ROLE_user")))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "fullName": "Иванов Иван Иванович",
                  "organization": "ФГБУ НИИ Экологии",
                  "position": "Ведущий инженер"
                }
                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.subjectId").value("user-1"))
        .andExpect(jsonPath("$.tenantId").value("tenant-1"))
        .andExpect(jsonPath("$.fullName").value("Иванов Иван Иванович"))
        .andExpect(jsonPath("$.organization").value("ФГБУ НИИ Экологии"))
        .andExpect(jsonPath("$.position").value("Ведущий инженер"))
        .andExpect(jsonPath("$.createdAt").exists())
        .andExpect(jsonPath("$.updatedAt").exists());

    mockMvc.perform(get("/api/v1/profile/me")
            .with(oauth2Login()
                .attributes(attrs -> {
                  attrs.put("sub", "user-1");
                  attrs.put("tenantId", "tenant-1");
                })
                .authorities(new SimpleGrantedAuthority("ROLE_user"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.fullName").value("Иванов Иван Иванович"));
  }

  // AC 3: PUT /profile/me updates existing profile
  @Test
  void upsertUpdatesExistingProfile() throws Exception {
    // Create profile
    mockMvc.perform(put("/api/v1/profile/me")
            .with(csrf())
            .with(oauth2Login()
                .attributes(attrs -> {
                  attrs.put("sub", "user-2");
                  attrs.put("tenantId", "tenant-1");
                })
                .authorities(new SimpleGrantedAuthority("ROLE_user")))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "fullName": "Петров Пётр",
                  "organization": "Организация 1",
                  "position": "Должность 1"
                }
                """))
        .andExpect(status().isOk());

    // Update profile
    mockMvc.perform(put("/api/v1/profile/me")
            .with(csrf())
            .with(oauth2Login()
                .attributes(attrs -> {
                  attrs.put("sub", "user-2");
                  attrs.put("tenantId", "tenant-1");
                })
                .authorities(new SimpleGrantedAuthority("ROLE_user")))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "fullName": "Петров Пётр Петрович",
                  "organization": "Организация 2",
                  "position": "Должность 2"
                }
                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.fullName").value("Петров Пётр Петрович"))
        .andExpect(jsonPath("$.organization").value("Организация 2"))
        .andExpect(jsonPath("$.position").value("Должность 2"));
  }

  // AC 9: Validation errors in RFC 7807 format
  @Test
  void upsertReturns400WhenFieldsBlank() throws Exception {
    mockMvc.perform(put("/api/v1/profile/me")
            .with(csrf())
            .with(oauth2Login()
                .attributes(attrs -> {
                  attrs.put("sub", "user-3");
                  attrs.put("tenantId", "tenant-1");
                })
                .authorities(new SimpleGrantedAuthority("ROLE_user")))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "fullName": "",
                  "organization": "",
                  "position": ""
                }
                """))
              .andExpect(status().isBadRequest())
              .andExpect(jsonPath("$.properties.errorCode").value("PROFILE_VALIDATION_FAILED"));
  }

  @Test
  void upsertReturns400WhenFieldsTooShort() throws Exception {
    mockMvc.perform(put("/api/v1/profile/me")
            .with(csrf())
            .with(oauth2Login()
                .attributes(attrs -> {
                  attrs.put("sub", "user-3");
                  attrs.put("tenantId", "tenant-1");
                })
                .authorities(new SimpleGrantedAuthority("ROLE_user")))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "fullName": "X",
                  "organization": "Y",
                  "position": "Z"
                }
                """))
              .andExpect(status().isBadRequest())
              .andExpect(jsonPath("$.properties.errorCode").value("PROFILE_VALIDATION_FAILED"));
  }

  // AC 9: Unauthenticated access denied
  @Test
  void getMyProfileReturns401WhenNotAuthenticated() throws Exception {
    mockMvc.perform(get("/api/v1/profile/me"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void putMyProfileReturns401WhenNotAuthenticated() throws Exception {
    mockMvc.perform(put("/api/v1/profile/me")
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "fullName": "Test",
                  "organization": "Test",
                  "position": "Test"
                }
                """))
        .andExpect(status().isUnauthorized());
  }
}
