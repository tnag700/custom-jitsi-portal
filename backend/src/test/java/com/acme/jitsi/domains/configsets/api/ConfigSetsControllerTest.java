package com.acme.jitsi.domains.configsets.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.acme.jitsi.shared.JwtTestProperties;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:testdb_configsets;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
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
      "APP_CONFIG_SETS_ENCRYPTION_KEY=0123456789ABCDEF0123456789ABCDEF"
    })
@AutoConfigureMockMvc
class ConfigSetsControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private StringRedisTemplate redisTemplate;

  @MockitoBean
  private ValueOperations<String, String> valueOperations;

  @BeforeEach
  void setUp() {
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
  }

  @Test
  void createConfigSetWithRequiredFieldsSucceeds() throws Exception {
    mockMvc.perform(post("/api/v1/config-sets")
            .with(csrf())
            .with(oauth2Login()
                .attributes(attrs -> {
                  attrs.put("sub", "admin-user");
                  attrs.put("tenantId", "tenant-1");
                })
                .authorities(new SimpleGrantedAuthority("ROLE_admin")))
            .header("Idempotency-Key", "configsets-create-1")
            .header("X-Trace-Id", "trace-configsets-create-1")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "name": "Config A",
                  "tenantId": "tenant-1",
                  "environmentType": "DEV",
                  "issuer": "issuer-a",
                  "audience": "audience-a",
                  "algorithm": "HS256",
                  "signingSecret": "secret-a",
                  "accessTtlMinutes": 20,
                  "refreshTtlMinutes": 120,
                  "meetingsServiceUrl": "https://meet.example.test"
                }
                """))
        .andExpect(status().isCreated())
        .andExpect(header().string("Location", org.hamcrest.Matchers.containsString("/api/v1/config-sets/")))
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.configSetId").isString())
        .andExpect(jsonPath("$.name").value("Config A"))
        .andExpect(jsonPath("$.tenantId").value("tenant-1"))
        .andExpect(jsonPath("$.environmentType").value("dev"))
        .andExpect(jsonPath("$.signingSecret").value("***"));
  }

  @Test
  void createConfigSetWithInvalidPayloadReturnsConfigSetInvalid() throws Exception {
    mockMvc.perform(post("/api/v1/config-sets")
            .with(csrf())
            .with(oauth2Login()
                .attributes(attrs -> {
                  attrs.put("sub", "admin-user");
                  attrs.put("tenantId", "tenant-1");
                })
                .authorities(new SimpleGrantedAuthority("ROLE_admin")))
            .header("Idempotency-Key", "configsets-create-2")
            .header("X-Trace-Id", "trace-configsets-invalid-1")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "name": "Config Invalid",
                  "tenantId": "tenant-1",
                  "environmentType": "DEV",
                  "issuer": "issuer-a",
                  "audience": "audience-a",
                  "algorithm": "HS256",
                  "accessTtlMinutes": 20,
                  "refreshTtlMinutes": 120,
                  "meetingsServiceUrl": "https://meet.example.test"
                }
                """))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.properties.errorCode").value("CONFIG_SET_INVALID"))
        .andExpect(jsonPath("$.properties.traceId").value("trace-configsets-invalid-1"));
  }

  @Test
  void getConfigSetByIdRequiresTenantAccess() throws Exception {
    mockMvc.perform(get("/api/v1/config-sets/non-existent")
            .with(csrf())
            .with(oauth2Login()
                .attributes(attrs -> {
                  attrs.put("sub", "admin-user");
                  attrs.put("tenantId", "tenant-1");
                })
                .authorities(new SimpleGrantedAuthority("ROLE_admin")))
            .header("X-Trace-Id", "trace-configsets-get-1"))
        .andExpect(status().isNotFound())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.properties.errorCode").value("CONFIG_SET_NOT_FOUND"));
  }

  @Test
  void updateConfigSetRequiresIdempotencyKey() throws Exception {
    mockMvc.perform(put("/api/v1/config-sets/cs-1")
            .with(csrf())
            .with(oauth2Login()
                .attributes(attrs -> {
                  attrs.put("sub", "admin-user");
                  attrs.put("tenantId", "tenant-1");
                })
                .authorities(new SimpleGrantedAuthority("ROLE_admin")))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "name": "Config Updated",
                  "tenantId": "tenant-1",
                  "environmentType": "DEV",
                  "issuer": "issuer-a",
                  "audience": "audience-a",
                  "algorithm": "HS256",
                  "signingSecret": "secret-a",
                  "accessTtlMinutes": 20,
                  "refreshTtlMinutes": 120,
                  "meetingsServiceUrl": "https://meet.example.test"
                }
                """))
        .andExpect(status().isBadRequest())
        .andExpect(content().string(org.hamcrest.Matchers.any(String.class)));
  }

  @Test
  void rolloutEndpointValidatesAndReturnsRolloutStatus() throws Exception {
    String configSetPayload = """
        {
          "name": "Rollout Config",
          "tenantId": "tenant-1",
          "environmentType": "DEV",
          "issuer": "https://portal.example.test",
          "audience": "jitsi-meet",
          "algorithm": "HS256",
          "roleClaim": "role",
          "signingSecret": "secret-a",
          "accessTtlMinutes": 20,
          "refreshTtlMinutes": 120,
          "meetingsServiceUrl": "https://meet.example.test/v1"
        }
        """;

    String body = mockMvc.perform(post("/api/v1/config-sets")
            .with(csrf())
            .with(oauth2Login()
                .attributes(attrs -> {
                  attrs.put("sub", "admin-user");
                  attrs.put("tenantId", "tenant-1");
                })
                .authorities(new SimpleGrantedAuthority("ROLE_admin")))
            .header("Idempotency-Key", "configsets-create-rollout-1")
            .header("X-Trace-Id", "trace-configsets-create-rollout-1")
            .contentType(MediaType.APPLICATION_JSON)
            .content(configSetPayload))
        .andExpect(status().isCreated())
        .andReturn()
        .getResponse()
        .getContentAsString();

    String configSetId = com.jayway.jsonpath.JsonPath.read(body, "$.configSetId");

    mockMvc.perform(post("/api/v1/config-sets/{configSetId}/rollout", configSetId)
            .with(csrf())
            .with(oauth2Login()
                .attributes(attrs -> {
                  attrs.put("sub", "admin-user");
                  attrs.put("tenantId", "tenant-1");
                })
                .authorities(new SimpleGrantedAuthority("ROLE_admin")))
            .header("Idempotency-Key", "configsets-rollout-1")
            .header("X-Trace-Id", "trace-configsets-rollout-1")
            .param("tenantId", "tenant-1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUCCEEDED"))
        .andExpect(jsonPath("$.configSetId").value(configSetId));
  }

  @Test
  void latestRolloutEndpointReturnsNotFoundWhenNoRollout() throws Exception {
    mockMvc.perform(get("/api/v1/config-sets/rollouts/latest")
            .with(csrf())
            .with(oauth2Login()
                .attributes(attrs -> {
                  attrs.put("sub", "admin-user");
                  attrs.put("tenantId", "tenant-1");
                })
                .authorities(new SimpleGrantedAuthority("ROLE_admin")))
            .param("tenantId", "tenant-1")
            .param("environmentType", "DEV")
            .header("X-Trace-Id", "trace-configsets-latest-1"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.properties.errorCode").value("CONFIG_SET_NOT_FOUND"));
  }
}