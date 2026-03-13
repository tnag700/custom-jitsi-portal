package com.acme.jitsi.observability;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class TracingBaselineSourceGuardTest {

  private static final Path BUILD_FILE = Path.of("build.gradle");
  private static final Path APPLICATION_FILE = Path.of("src/main/resources/application.yml");
  private static final Path OTLP_EXPORT_TEST_FILE =
      Path.of("src/test/java/com/acme/jitsi/observability/OtlpTracingExportIntegrationTest.java");
  private static final Path IDEMPOTENCY_TRACING_TEST_FILE =
      Path.of("src/test/java/com/acme/jitsi/observability/IdempotencyTracingIntegrationTest.java");
  private static final Path REDIS_TRACING_WIRING_TEST_FILE =
      Path.of("src/test/java/com/acme/jitsi/observability/RedisTracingWiringIntegrationTest.java");

  @Test
  void buildAndApplicationConfigDeclareTracingBaselineDependenciesAndProperties() throws IOException {
    String buildGradle = Files.readString(BUILD_FILE);
    String applicationYaml = Files.readString(APPLICATION_FILE);
    String otlpExportTest = Files.readString(OTLP_EXPORT_TEST_FILE);
    String idempotencyTracingTest = Files.readString(IDEMPOTENCY_TRACING_TEST_FILE);
    String redisTracingWiringTest = Files.readString(REDIS_TRACING_WIRING_TEST_FILE);

    assertThat(buildGradle)
        .contains("spring-boot-starter-data-redis")
        .contains("spring-boot-starter-opentelemetry")
        .contains("datasource-micrometer-spring-boot")
        .contains("datasource-micrometer-opentelemetry");

    assertThat(applicationYaml)
        .contains("management:")
        .contains("tracing:")
        .contains("sampling:")
        .contains("probability:")
        .contains("opentelemetry:")
        .contains("export:")
        .contains("otlp:")
        .contains("endpoint:")
                .contains("transport:")
                .doesNotContain("http/protobuf")
        .contains("logging:")
        .contains("pattern:")
        .contains("correlation:");

      assertThat(otlpExportTest)
        .contains("management.tracing.export.otlp.enabled=true")
                .contains("management.opentelemetry.tracing.export.otlp.transport=http")
        .contains("/v1/traces")
        .contains("application/x-protobuf")
        .contains("awaitRecordedRequest");

    assertThat(idempotencyTracingTest)
        .contains("@AutoConfigureTracing")
        .contains("FakeRedisServer")
        .contains("TestSpanExporter")
        .contains("/api/v1/test/idempotent")
        .contains("SpanKind.SERVER")
        .contains("observedCommands");

    assertThat(redisTracingWiringTest)
        .contains("LettuceConnectionFactory")
        .contains("getClientResources()")
        .contains("tracing()")
        .contains("NoOpTracing");
  }
}