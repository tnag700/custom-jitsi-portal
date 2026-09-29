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
  private static final Path CONTAINER_SUPPORT_FILE =
      Path.of("src/test/java/com/acme/jitsi/support/PostgresRedisContainerIntegrationTestSupport.java");

  @Test
  void buildAndApplicationConfigDeclareTracingBaselineDependenciesAndProperties() throws IOException {
    String buildGradle = Files.readString(BUILD_FILE);
    String applicationYaml = Files.readString(APPLICATION_FILE);
    String otlpExportTest = Files.readString(OTLP_EXPORT_TEST_FILE);
    String idempotencyTracingTest = Files.readString(IDEMPOTENCY_TRACING_TEST_FILE);
    String redisTracingWiringTest = Files.readString(REDIS_TRACING_WIRING_TEST_FILE);
    String containerSupport = Files.readString(CONTAINER_SUPPORT_FILE);

    assertThat(buildGradle)
        .contains("spring-boot-starter-data-redis")
        .contains("spring-boot-starter-opentelemetry")
        .contains("datasource-micrometer-spring-boot")
        .contains("datasource-micrometer-opentelemetry");

    assertThat(applicationYaml)
        .contains("management:")
        .contains("jdbc:")
        .contains("metrics:")
        .contains("enabled: false")
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
        .contains("console: ecs")
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
        .contains("extends PostgresRedisContainerIntegrationTestSupport")
        .contains("TestSpanExporter")
        .contains("/api/v1/test/idempotent")
        .contains("SpanKind.SERVER")
        .contains("matchesIdempotencyDatabaseMutation")
        .contains("db.operation.name");

    assertThat(containerSupport)
        .contains("@Testcontainers")
        .contains("@DynamicPropertySource")
        .contains("PostgreSQLContainer")
        .contains("GenericContainer")
        .contains("postgres@sha256:5a5a84b19854a9ffaa54082c166ff4ec27473a361e496e5ea167f298f2da9722")
        .contains("redis@sha256:d5ac52db24d4e70566fe9944f22cf5bdc2bc739b05c0f426335161ea6c23f3b3");

    assertThat(redisTracingWiringTest)
        .contains("LettuceConnectionFactory")
        .contains("getClientResources()")
        .contains("tracing()")
        .contains("NoOpTracing");
  }
}
