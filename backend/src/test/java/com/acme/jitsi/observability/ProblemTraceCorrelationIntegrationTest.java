package com.acme.jitsi.observability;

import static com.acme.jitsi.shared.JwtTestProperties.CONTOUR_ACCESS_TTL_MINUTES;
import static com.acme.jitsi.shared.JwtTestProperties.CONTOUR_ALGORITHM;
import static com.acme.jitsi.shared.JwtTestProperties.CONTOUR_AUDIENCE;
import static com.acme.jitsi.shared.JwtTestProperties.CONTOUR_ISSUER;
import static com.acme.jitsi.shared.JwtTestProperties.CONTOUR_REFRESH_TTL_MINUTES;
import static com.acme.jitsi.shared.JwtTestProperties.CONTOUR_ROLE_CLAIM;
import static com.acme.jitsi.shared.JwtTestProperties.TOKEN_ALGORITHM;
import static com.acme.jitsi.shared.JwtTestProperties.TOKEN_AUDIENCE;
import static com.acme.jitsi.shared.JwtTestProperties.TOKEN_ISSUER;
import static com.acme.jitsi.shared.JwtTestProperties.TOKEN_ROLE_CLAIM_NAME;
import static com.acme.jitsi.shared.JwtTestProperties.TOKEN_SIGNING_SECRET;
import static com.acme.jitsi.shared.JwtTestProperties.TOKEN_TTL_MINUTES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.tracing.test.autoconfigure.AutoConfigureTracing;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:trace-problem;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
      "spring.datasource.driver-class-name=org.h2.Driver",
      "spring.jpa.hibernate.ddl-auto=validate",
      "spring.flyway.enabled=true",
      "management.health.redis.enabled=false",
      "management.opentelemetry.tracing.export.schedule-delay=10ms",
      "management.opentelemetry.tracing.export.max-batch-size=1",
      TOKEN_SIGNING_SECRET,
      TOKEN_ISSUER,
      TOKEN_AUDIENCE,
      TOKEN_ALGORITHM,
      TOKEN_TTL_MINUTES,
      TOKEN_ROLE_CLAIM_NAME,
      CONTOUR_ISSUER,
      CONTOUR_AUDIENCE,
      CONTOUR_ROLE_CLAIM,
      CONTOUR_ALGORITHM,
      CONTOUR_ACCESS_TTL_MINUTES,
      CONTOUR_REFRESH_TTL_MINUTES
    })
@AutoConfigureMockMvc
@AutoConfigureTracing
@Import(TestTracingConfiguration.class)
class ProblemTraceCorrelationIntegrationTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private TestSpanExporter testSpanExporter;

  @Autowired
  private ObjectMapper objectMapper;

  @Test
  void unauthorizedProblemPayloadUsesSpanTraceIdAndPreservesRequestId() throws Exception {
    testSpanExporter.reset();

    String responseBody = mockMvc.perform(get("/api/v1/auth/me")
            .header("X-Trace-Id", "client-auth-request-1"))
        .andExpect(status().isUnauthorized())
        .andReturn()
        .getResponse()
        .getContentAsString();

    @SuppressWarnings("unchecked")
    Map<String, Object> payload = objectMapper.readValue(responseBody, Map.class);
    @SuppressWarnings("unchecked")
    Map<String, Object> properties = (Map<String, Object>) payload.get("properties");

    List<SpanData> spans = testSpanExporter.await(
        exported -> exported.stream().anyMatch(span -> span.getKind() == SpanKind.SERVER),
        Duration.ofSeconds(5));

    assertThat(properties.get("requestId")).isEqualTo("client-auth-request-1");
    assertThat(properties.get("traceId")).isInstanceOf(String.class);
    assertThat(spans)
        .filteredOn(span -> span.getKind() == SpanKind.SERVER)
        .extracting(SpanData::getTraceId)
        .contains((String) properties.get("traceId"));
  }
}