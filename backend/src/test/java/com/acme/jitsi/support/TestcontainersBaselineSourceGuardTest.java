package com.acme.jitsi.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.testcontainers.utility.DockerImageName;

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
  void pinnedImageNamesInitializeWithoutDockerAndRetainOfficialCompatibility() {
    PostgresRedisContainerIntegrationTestSupport.POSTGRES_IMAGE.assertValid();
    PostgresRedisContainerIntegrationTestSupport.POSTGRES_IMAGE.assertCompatibleWith(DockerImageName.parse("postgres"));
    PostgresRedisContainerIntegrationTestSupport.REDIS_IMAGE.assertValid();
    PostgresRedisContainerIntegrationTestSupport.REDIS_IMAGE.assertCompatibleWith(DockerImageName.parse("redis"));
  }

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
        .contains("org.testcontainers:testcontainers-junit-jupiter:2.0.5")
        .contains("org.testcontainers:testcontainers-postgresql:2.0.5");

    assertThat(sharedSupport)
        .contains("@Testcontainers")
        .contains("@ActiveProfiles(\"test\")")
        .contains("@DynamicPropertySource")
        .contains("PostgreSQLContainer")
        .contains("getJdbcUrl")
        .contains("postgres@sha256:5a5a84b19854a9ffaa54082c166ff4ec27473a361e496e5ea167f298f2da9722")
        .contains("redis@sha256:d5ac52db24d4e70566fe9944f22cf5bdc2bc739b05c0f426335161ea6c23f3b3")
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
