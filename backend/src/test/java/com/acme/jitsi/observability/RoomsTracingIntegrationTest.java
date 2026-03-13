package com.acme.jitsi.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.acme.jitsi.shared.JwtTestProperties;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.tracing.test.autoconfigure.AutoConfigureTracing;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:trace-rooms;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
      "spring.datasource.driver-class-name=org.h2.Driver",
      "spring.jpa.hibernate.ddl-auto=validate",
      "spring.flyway.enabled=true",
      "management.health.redis.enabled=false",
      "management.opentelemetry.tracing.export.schedule-delay=10ms",
      "management.opentelemetry.tracing.export.max-batch-size=1",
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
      "app.rooms.config-sets.config-2.role-claim=role"
    })
@AutoConfigureMockMvc
@AutoConfigureTracing
@Import(TestTracingConfiguration.class)
class RoomsTracingIntegrationTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private TestSpanExporter testSpanExporter;

  @Test
  void createRoomProducesServerAndJdbcSpansInSameTrace() throws Exception {
    testSpanExporter.reset();

    mockMvc.perform(post("/api/v1/rooms")
            .with(csrf())
            .with(oauth2Login()
                .attributes(attrs -> attrs.put("sub", "trace-admin"))
                .authorities(new SimpleGrantedAuthority("ROLE_admin")))
            .header("X-Trace-Id", "trace-room-span-1")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "name": "Tracing Room",
                  "tenantId": "tenant-1",
                  "configSetId": "config-1"
                }
                """))
        .andExpect(status().isCreated());

    List<SpanData> spans = testSpanExporter.await(
        exported -> exported.stream().filter(span -> span.getKind() != SpanKind.SERVER).findAny().isPresent(),
        Duration.ofSeconds(5));

    Map<String, List<SpanData>> spansByTrace = spans.stream().collect(Collectors.groupingBy(SpanData::getTraceId));

    assertThat(spansByTrace.values())
        .anySatisfy(traceSpans -> {
          assertThat(traceSpans)
              .anySatisfy(span -> assertThat(span.getKind()).isEqualTo(SpanKind.SERVER));
          assertThat(traceSpans)
              .anySatisfy(span -> assertThat(span.getKind()).isNotEqualTo(SpanKind.SERVER));
        });
  }
}