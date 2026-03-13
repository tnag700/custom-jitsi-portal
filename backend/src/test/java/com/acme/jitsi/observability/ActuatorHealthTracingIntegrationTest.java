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

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.tracing.test.autoconfigure.AutoConfigureTracing;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "spring.datasource.url=jdbc:h2:mem:trace-health;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
      "spring.datasource.driver-class-name=org.h2.Driver",
      "spring.jpa.hibernate.ddl-auto=validate",
      "spring.flyway.enabled=true",
      "management.health.db.enabled=false",
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
@AutoConfigureTracing
@Import(TestTracingConfiguration.class)
class ActuatorHealthTracingIntegrationTest {

  @LocalServerPort
  private int port;

  @Autowired
  private TestSpanExporter testSpanExporter;

  private final RestTemplate restTemplate = new RestTemplate();

  @Test
  void actuatorHealthRequestProducesHttpServerSpan() throws Exception {
    testSpanExporter.reset();

    ResponseEntity<String> response = restTemplate.getForEntity(
        "http://localhost:" + port + "/actuator/health",
        String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

    List<SpanData> spans = testSpanExporter.await(
        exported -> exported.stream().anyMatch(span -> span.getKind() == SpanKind.SERVER),
        Duration.ofSeconds(5));

    assertThat(spans)
        .anySatisfy(span -> assertThat(span.getKind()).isEqualTo(SpanKind.SERVER));
  }
}