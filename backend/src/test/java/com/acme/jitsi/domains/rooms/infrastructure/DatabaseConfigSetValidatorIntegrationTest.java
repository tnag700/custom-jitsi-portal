package com.acme.jitsi.domains.rooms.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.acme.jitsi.domains.configsets.service.ConfigSet;
import com.acme.jitsi.domains.configsets.service.ConfigSetEnvironmentType;
import com.acme.jitsi.domains.configsets.service.ConfigSetRepository;
import com.acme.jitsi.domains.configsets.service.ConfigSetStatus;
import com.acme.jitsi.domains.rooms.service.ConfigSetValidator;
import com.acme.jitsi.shared.JwtTestProperties;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:testdb_configset_validator;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
      "spring.datasource.driver-class-name=org.h2.Driver",
      "spring.jpa.hibernate.ddl-auto=validate",
      "spring.flyway.enabled=true",
      "management.health.redis.enabled=false",
      "app.features.config-sets-from-db=true",
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
      "app.rooms.config-sets.config-2.role-claim=role",
      "APP_CONFIG_SETS_ENCRYPTION_KEY=0123456789ABCDEF0123456789ABCDEF"
    })
class DatabaseConfigSetValidatorIntegrationTest {

  @Autowired
  private ConfigSetValidator validator;

  @Autowired
  private ConfigSetRepository repository;

  @Autowired
  private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void setUp() {
    jdbcTemplate.execute("DELETE FROM config_set_compatibility_checks");
    jdbcTemplate.execute("DELETE FROM config_set_rollouts");
    jdbcTemplate.execute("DELETE FROM config_set_audit_events");
    jdbcTemplate.execute("DELETE FROM config_sets");
  }

  @Test
  void usesDatabaseValidatorBeanAndAcceptsActiveAndDraftStatusesThroughTranslatorBackedRepository() {
    repository.save(configSet("cs-active", ConfigSetStatus.ACTIVE, "active-secret"));
    repository.save(configSet("cs-draft", ConfigSetStatus.DRAFT, "draft-secret"));

    assertThat(validator).isInstanceOf(DatabaseConfigSetValidator.class);
    assertThat(validator.isValid("cs-active")).isTrue();
    assertThat(validator.isValid("cs-draft")).isTrue();
  }

  @Test
  void rejectsInactiveStatusThroughTranslatorBackedRepository() {
    repository.save(configSet("cs-inactive", ConfigSetStatus.INACTIVE, "inactive-secret"));

    assertThat(validator.isValid("cs-inactive")).isFalse();
  }

  private ConfigSet configSet(String configSetId, ConfigSetStatus status, String signingSecret) {
    Instant createdAt = Instant.parse("2026-03-10T10:15:30Z");
    Instant updatedAt = Instant.parse("2026-03-10T10:15:30Z");
    return new ConfigSet(
        configSetId,
        "Config " + configSetId,
        "tenant-validator",
        ConfigSetEnvironmentType.DEV,
        "https://issuer.example.test/" + configSetId,
        "audience-" + configSetId,
        "HS256",
        "role",
        signingSecret,
        null,
        20,
        120,
        "https://meet.example.test/" + configSetId,
        status,
        createdAt,
        updatedAt);
  }
}