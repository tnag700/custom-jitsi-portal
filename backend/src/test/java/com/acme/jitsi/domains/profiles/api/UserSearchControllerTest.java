package com.acme.jitsi.domains.profiles.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

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
      "spring.datasource.url=jdbc:h2:mem:testdb-usersearch;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
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
class UserSearchControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void setUp() {
    jdbcTemplate.execute("DELETE FROM user_profiles");
  }

  private void createProfile(String subjectId, String tenantId, String fullName, String organization, String position) throws Exception {
    mockMvc.perform(put("/api/v1/profile/me")
            .with(csrf())
            .with(oauth2Login()
                .attributes(attrs -> {
                  attrs.put("sub", subjectId);
                  attrs.put("tenantId", tenantId);
                })
                .authorities(new SimpleGrantedAuthority("ROLE_user")))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "fullName": "%s",
                  "organization": "%s",
                  "position": "%s"
                }
                """.formatted(fullName, organization, position)))
        .andExpect(status().isOk());
  }

  // AC 4: Search users by fullName
  @Test
  void searchUsersByQuery() throws Exception {
    createProfile("sub-1", "tenant-1", "Иванов Иван", "МВД", "Оперативник");
    createProfile("sub-2", "tenant-1", "Иванова Мария", "МВД", "Аналитик");
    createProfile("sub-3", "tenant-1", "Петров Пётр", "ФГБУ", "Инженер");

    mockMvc.perform(get("/api/v1/users/search")
            .param("tenant_id", "tenant-1")
            .param("q", "Иван")
            .with(oauth2Login()
                .attributes(attrs -> {
                  attrs.put("sub", "admin-user");
                  attrs.put("tenantId", "tenant-1");
                })
                .authorities(new SimpleGrantedAuthority("ROLE_user"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].fullName").exists())
        .andExpect(jsonPath("$[0].subjectId").exists())
        .andExpect(jsonPath("$[0].organization").exists())
        .andExpect(jsonPath("$[0].position").exists());
  }

  // AC 4: Search by organization
  @Test
  void searchUsersByOrganization() throws Exception {
    createProfile("sub-4", "tenant-1", "Сидоров Сидор", "ФГБУ", "Инженер");
    createProfile("sub-5", "tenant-1", "Козлов Козёл", "МВД", "Начальник");

    mockMvc.perform(get("/api/v1/users/search")
            .param("tenant_id", "tenant-1")
            .param("organization", "ФГБУ")
            .with(oauth2Login()
                .attributes(attrs -> {
                  attrs.put("sub", "admin-user");
                  attrs.put("tenantId", "tenant-1");
                })
                .authorities(new SimpleGrantedAuthority("ROLE_user"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].organization").value("ФГБУ"));
  }

  @Test
  void searchUsersReturnsAllTenantUsersWhenQueryIsOmitted() throws Exception {
    createProfile("sub-1", "tenant-1", "Иванов Иван", "МВД", "Оперативник");
    createProfile("sub-2", "tenant-1", "Петров Пётр", "ФГБУ", "Инженер");

    mockMvc.perform(get("/api/v1/users/search")
            .param("tenant_id", "tenant-1")
            .with(oauth2Login()
                .attributes(attrs -> {
                  attrs.put("sub", "admin-user");
                  attrs.put("tenantId", "tenant-1");
                })
                .authorities(new SimpleGrantedAuthority("ROLE_user"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2));
  }

  // Tenant isolation
  @Test
  void searchUsersDeniedForDifferentTenant() throws Exception {
    mockMvc.perform(get("/api/v1/users/search")
            .param("tenant_id", "other-tenant")
            .with(oauth2Login()
                .attributes(attrs -> {
                  attrs.put("sub", "admin-user");
                  attrs.put("tenantId", "tenant-1");
                })
                .authorities(new SimpleGrantedAuthority("ROLE_user"))))
        .andExpect(status().isForbidden());
  }

  // Empty results
  @Test
  void searchUsersReturnsEmptyForNoMatches() throws Exception {
    mockMvc.perform(get("/api/v1/users/search")
            .param("tenant_id", "tenant-1")
            .param("q", "СовершенноНесуществующийПользователь")
            .with(oauth2Login()
                .attributes(attrs -> {
                  attrs.put("sub", "admin-user");
                  attrs.put("tenantId", "tenant-1");
                })
                .authorities(new SimpleGrantedAuthority("ROLE_user"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
  }

  // Unauthenticated
  @Test
  void searchUsersReturns401WhenNotAuthenticated() throws Exception {
    mockMvc.perform(get("/api/v1/users/search")
            .param("tenant_id", "tenant-1"))
        .andExpect(status().isUnauthorized());
  }
}
