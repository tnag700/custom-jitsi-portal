package com.acme.jitsi.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class TestcontainersBaselineSourceGuardTest {

  private static final Path BUILD_FILE = Path.of("build.gradle");
  private static final Path SHARED_SUPPORT_FILE =
      Path.of("src/test/java/com/acme/jitsi/support/PostgresRedisContainerIntegrationTestSupport.java");
  private static final Path MEETINGS_SUPPORT_FILE =
      Path.of("src/test/java/com/acme/jitsi/domains/meetings/api/RedisBackedMeetingApiIntegrationTestSupport.java");
  private static final Path IDEMPOTENCY_TEST_FILE =
      Path.of("src/test/java/com/acme/jitsi/infrastructure/idempotency/IdempotencyIntegrationTest.java");
  private static final Path IDEMPOTENCY_TRACING_TEST_FILE =
      Path.of("src/test/java/com/acme/jitsi/observability/IdempotencyTracingIntegrationTest.java");
  private static final Path MEETING_INVITES_TEST_FILE =
      Path.of("src/test/java/com/acme/jitsi/domains/meetings/api/MeetingInvitesControllerTest.java");
  private static final Path MEETINGS_CONTROLLER_TEST_FILE =
      Path.of("src/test/java/com/acme/jitsi/domains/meetings/api/MeetingsControllerTest.java");
  private static final Path README_FILE = Path.of("..", "README.md");

  @Test
  void canonicalContainerBaselineIsDocumentedInCodeAndDocs() throws IOException {
    String buildGradle = Files.readString(BUILD_FILE);
    String sharedSupport = Files.readString(SHARED_SUPPORT_FILE);
    String meetingsSupport = Files.readString(MEETINGS_SUPPORT_FILE);
    String idempotencyTest = Files.readString(IDEMPOTENCY_TEST_FILE);
    String idempotencyTracingTest = Files.readString(IDEMPOTENCY_TRACING_TEST_FILE);
    String meetingInvitesControllerTest = Files.readString(MEETING_INVITES_TEST_FILE);
    String meetingsControllerTest = Files.readString(MEETINGS_CONTROLLER_TEST_FILE);
    String readme = Files.readString(README_FILE);

    assertThat(buildGradle)
        .contains("org.testcontainers:junit-jupiter:1.21.4")
        .contains("org.testcontainers:postgresql:1.21.4");

    assertThat(sharedSupport)
        .contains("@Testcontainers")
        .contains("@ActiveProfiles(\"test\")")
        .contains("@DynamicPropertySource")
        .contains("PostgreSQLContainer")
        .contains("getJdbcUrl")
        .contains("postgres:18-alpine")
        .contains("redis:7-alpine")
        .contains("spring.datasource.url")
        .contains("spring.data.redis.host");

    assertThat(meetingsSupport)
        .contains("extends PostgresRedisContainerIntegrationTestSupport")
        .doesNotContain("DynamicPropertySource")
        .doesNotContain("GenericContainer");

    assertThat(idempotencyTest)
        .contains("extends PostgresRedisContainerIntegrationTestSupport")
        .contains("@Tag(\"container\")")
        .doesNotContain("FakeRedisServer")
        .doesNotContain("DynamicPropertySource");

    assertThat(idempotencyTracingTest)
        .contains("extends PostgresRedisContainerIntegrationTestSupport")
        .contains("@Tag(\"container\")")
        .doesNotContain("FakeRedisServer")
        .doesNotContain("DynamicPropertySource");

    assertThat(meetingInvitesControllerTest)
        .contains("@Tag(\"container\")")
        .doesNotContain("spring.datasource.url=jdbc:h2:mem:");

    assertThat(meetingsControllerTest)
        .contains("@Tag(\"container\")")
        .doesNotContain("spring.datasource.url=jdbc:h2:mem:");

    assertThat(readme)
        .contains("testContainer")
        .contains("Docker")
        .contains("Testcontainers")
        .contains("unit / slice / non-container integration / container");
  }
}