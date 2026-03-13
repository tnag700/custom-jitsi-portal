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

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestTemplate;

@org.junit.jupiter.api.Tag("integration")
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "spring.datasource.url=jdbc:h2:mem:trace-otlp-export;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
      "spring.datasource.driver-class-name=org.h2.Driver",
      "spring.jpa.hibernate.ddl-auto=validate",
      "spring.flyway.enabled=true",
      "management.health.db.enabled=false",
      "management.health.redis.enabled=false",
      "management.tracing.sampling.probability=1.0",
      "management.tracing.export.otlp.enabled=true",
      "management.opentelemetry.tracing.export.schedule-delay=10ms",
      "management.opentelemetry.tracing.export.max-batch-size=1",
      "management.opentelemetry.tracing.export.otlp.transport=http",
      "management.opentelemetry.tracing.export.otlp.timeout=5s",
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
class OtlpTracingExportIntegrationTest {

  private static final byte[] EMPTY_PROTO_RESPONSE = new byte[0];

  private static HttpServer collectorServer;
  private static final List<RecordedOtlpRequest> recordedRequests = new CopyOnWriteArrayList<>();

  @LocalServerPort
  private int port;

  private final RestTemplate restTemplate = new RestTemplate();

  @BeforeAll
  static void startCollector() throws IOException {
    ensureCollectorStarted();
  }

  @AfterAll
  static void stopCollector() {
    if (collectorServer != null) {
      collectorServer.stop(0);
    }
    recordedRequests.clear();
  }

  @DynamicPropertySource
  static void otlpProperties(DynamicPropertyRegistry registry) {
    ensureCollectorStarted();
    registry.add(
        "management.opentelemetry.tracing.export.otlp.endpoint",
        () -> "http://localhost:" + collectorServer.getAddress().getPort() + "/v1/traces");
  }

  private static void ensureCollectorStarted() {
    if (collectorServer != null) {
      return;
    }
    try {
      collectorServer = HttpServer.create(new InetSocketAddress(0), 0);
      collectorServer.createContext("/v1/traces", new RecordingTraceHandler());
      collectorServer.setExecutor(Executors.newCachedThreadPool());
      collectorServer.start();
    } catch (IOException ioException) {
      throw new IllegalStateException("Failed to start OTLP collector stub", ioException);
    }
  }

  @Test
  void actuatorHealthTraceIsExportedToConfiguredOtlpSink() {
    recordedRequests.clear();

    ResponseEntity<String> response = restTemplate.getForEntity(
        "http://localhost:" + port + "/actuator/health",
        String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

    RecordedOtlpRequest exportedRequest = awaitRecordedRequest(Duration.ofSeconds(5));

    assertThat(exportedRequest.contentType()).contains("application/x-protobuf");
    assertThat(exportedRequest.body()).isNotEmpty();
  }

  private RecordedOtlpRequest awaitRecordedRequest(Duration timeout) {
    long deadline = System.nanoTime() + timeout.toNanos();
    while (System.nanoTime() < deadline) {
      if (!recordedRequests.isEmpty()) {
        return recordedRequests.getLast();
      }
      try {
        Thread.sleep(25L);
      } catch (InterruptedException interruptedException) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("Interrupted while waiting for OTLP export", interruptedException);
      }
    }
    throw new AssertionError("Timed out waiting for OTLP trace export request");
  }

  private record RecordedOtlpRequest(String contentType, byte[] body) {
  }

  private static final class RecordingTraceHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange exchange) throws IOException {
      byte[] requestBody = exchange.getRequestBody().readAllBytes();
      recordedRequests.add(new RecordedOtlpRequest(exchange.getRequestHeaders().getFirst("Content-Type"), requestBody));

      exchange.sendResponseHeaders(200, EMPTY_PROTO_RESPONSE.length);
      try (OutputStream responseBody = exchange.getResponseBody()) {
        responseBody.write(EMPTY_PROTO_RESPONSE);
      }
    }
  }
}