package com.acme.jitsi.observability;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.trace.data.SpanData;
import com.acme.jitsi.infrastructure.idempotency.IdempotencyTestController;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.tracing.test.autoconfigure.AutoConfigureTracing;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestTemplate;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "app.idempotency.ttl=1s",
        "app.meetings.token.algorithm=HS256",
        "app.meetings.token.signing-secret=01234567890123456789012345678901",
        "app.meetings.token.issuer=https://portal.test.local",
        "app.meetings.token.audience=jitsi-meet",
        "app.security.jwt-contour.issuer=https://portal.test.local",
        "app.security.jwt-contour.audience=jitsi-meet",
        "app.security.jwt-contour.role-claim=role",
        "app.security.jwt-contour.algorithm=HS256",
        "app.security.jwt-contour.access-ttl-minutes=20",
        "app.security.jwt-contour.refresh-ttl-minutes=60",
        "management.opentelemetry.tracing.export.schedule-delay=10ms",
        "management.opentelemetry.tracing.export.max-batch-size=1"
    })
@AutoConfigureTracing
@Import({
    IdempotencyTestController.class,
    TestTracingConfiguration.class,
    IdempotencyTracingIntegrationTest.IdempotencyTracingTestSecurityConfiguration.class
})
@Tag("integration")
@ActiveProfiles("test")
class IdempotencyTracingIntegrationTest {

    private static final String IDEMPOTENT_PATH = "/api/v1/test/idempotent";
    private static final AttributeKey<String> DB_SYSTEM = AttributeKey.stringKey("db.system");
    private static final AttributeKey<String> HTTP_REQUEST_METHOD = AttributeKey.stringKey("http.request.method");
    private static final AttributeKey<String> URL_PATH = AttributeKey.stringKey("url.path");

    private static FakeRedisServer redis;

    @BeforeAll
    static void startFakeRedis() throws IOException {
        ensureRedisStarted();
    }

    @AfterAll
    static void stopFakeRedis() throws IOException {
        if (redis != null) {
            redis.close();
        }
    }

  @DynamicPropertySource
  static void redisProperties(DynamicPropertyRegistry registry) {
        ensureRedisStarted();
        registry.add("spring.data.redis.host", () -> "127.0.0.1");
        registry.add("spring.data.redis.port", () -> redis.getPort());
  }

    private static void ensureRedisStarted() {
        if (redis != null) {
            return;
        }
        try {
            redis = FakeRedisServer.start();
        } catch (IOException ioException) {
            throw new IllegalStateException("Failed to start fake Redis server", ioException);
        }
    }

  @Autowired
    private TestSpanExporter testSpanExporter;

    @LocalServerPort
    private int port;

    private final RestTemplate restTemplate = new RestTemplate();

  @Test
  void idempotentRequestProducesServerAndRedisSpansInSameTrace() throws Exception {
    testSpanExporter.reset();

        ResponseEntity<String> response = restTemplate.exchange(
                RequestEntity.post(URI.create("http://localhost:" + port + IDEMPOTENT_PATH))
                        .header("Idempotency-Key", "trace-redis-key-1")
                        .build(),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

    List<SpanData> spans = testSpanExporter.await(
        exported -> exported.stream()
            .filter(IdempotencyTracingIntegrationTest::isIdempotentServerSpan)
            .map(SpanData::getTraceId)
            .anyMatch(traceId -> exported.stream()
                .anyMatch(span -> traceId.equals(span.getTraceId()) && isRedisSpan(span))),
        Duration.ofSeconds(5));

    String traceId = spans.stream()
        .filter(IdempotencyTracingIntegrationTest::isIdempotentServerSpan)
        .map(SpanData::getTraceId)
        .filter(serverTraceId -> spans.stream().anyMatch(span -> serverTraceId.equals(span.getTraceId()) && isRedisSpan(span)))
        .findFirst()
        .orElseThrow(() -> new AssertionError("No idempotent request trace with Redis spans exported. Spans: " + summarizeSpans(spans)));

    List<SpanData> traceSpans = spans.stream()
        .filter(span -> traceId.equals(span.getTraceId()))
        .toList();

    assertThat(traceSpans)
        .anySatisfy(span -> assertThat(span.getKind()).isEqualTo(SpanKind.SERVER));
    assertThat(traceSpans)
        .anySatisfy(span -> assertThat(isRedisSpan(span)).isTrue());
    assertThat(redis.observedCommands())
        .anySatisfy(command -> assertThat(command).startsWith("SET idempotency:POST:/api/v1/test/idempotent:trace-redis-key-1"));
  }

  private static boolean isIdempotentServerSpan(SpanData span) {
    if (span.getKind() != SpanKind.SERVER) {
      return false;
    }

    String path = span.getAttributes().get(URL_PATH);
        String method = span.getAttributes().get(HTTP_REQUEST_METHOD);
        return IDEMPOTENT_PATH.equals(path)
                || span.getName().contains(IDEMPOTENT_PATH)
                || ("POST".equals(method) && span.getName().toLowerCase().contains("idempotent"));
  }

  private static boolean isRedisSpan(SpanData span) {
    return "redis".equalsIgnoreCase(span.getAttributes().get(DB_SYSTEM));
  }

    private static String summarizeSpans(List<SpanData> spans) {
        return spans.stream()
                .map(span -> span.getKind() + "|" + span.getName()
                        + "|method=" + span.getAttributes().get(HTTP_REQUEST_METHOD)
                        + "|path=" + span.getAttributes().get(URL_PATH)
                        + "|db=" + span.getAttributes().get(DB_SYSTEM)
                        + "|trace=" + span.getTraceId())
                .collect(Collectors.joining(", "));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class IdempotencyTracingTestSecurityConfiguration {

        @Bean
        @Order(0)
        SecurityFilterChain idempotencyTestSecurityFilterChain(HttpSecurity http) throws Exception {
            return http
                    .securityMatcher(IDEMPOTENT_PATH)
                    .csrf(csrf -> csrf.disable())
                    .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                    .build();
        }
    }
}