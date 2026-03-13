package com.acme.jitsi.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.acme.jitsi.domains.configsets.service.ConfigCompatibilityCheckResult;
import com.acme.jitsi.domains.configsets.service.ConfigCompatibilityMismatch;
import com.acme.jitsi.domains.configsets.service.ConfigCompatibilityMismatchCode;
import com.acme.jitsi.domains.configsets.service.ConfigSet;
import com.acme.jitsi.domains.configsets.service.ConfigSetCompatibilityStateService;
import com.acme.jitsi.domains.configsets.service.ConfigSetDryRunValidator;
import com.acme.jitsi.domains.configsets.service.ConfigSetEnvironmentType;
import com.acme.jitsi.domains.configsets.service.ConfigSetRepository;
import com.acme.jitsi.domains.configsets.service.ConfigSetStatus;
import com.acme.jitsi.shared.JwtTestProperties;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:testdb_active_configset_startup;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
      "spring.datasource.driver-class-name=org.h2.Driver",
      "spring.jpa.hibernate.ddl-auto=validate",
      "spring.flyway.enabled=true",
      "management.health.redis.enabled=false",
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
class ActiveConfigSetStartupValidatorRepositoryIntegrationTest {

  @Autowired
  private ActiveConfigSetStartupValidator validator;

  @Autowired
  private ConfigSetRepository repository;

  @Autowired
  private JdbcTemplate jdbcTemplate;

  @MockitoBean
  private ConfigSetDryRunValidator configSetDryRunValidator;

  @MockitoBean
  private ConfigSetCompatibilityStateService compatibilityStateService;

  @BeforeEach
  void setUp() {
    jdbcTemplate.execute("DELETE FROM config_set_compatibility_checks");
    jdbcTemplate.execute("DELETE FROM config_set_rollouts");
    jdbcTemplate.execute("DELETE FROM config_set_audit_events");
    jdbcTemplate.execute("DELETE FROM config_sets");
    reset(configSetDryRunValidator, compatibilityStateService);
  }

  @Test
  void afterPropertiesSetReadsPlaintextSecretThroughRealRepositoryAndRecordsCompatibilitySnapshot() {
    ConfigSet configSet = repository.save(activeConfigSet("cs-startup-ok", "plain-secret"));
    ConfigCompatibilityCheckResult result = new ConfigCompatibilityCheckResult(
        true,
        List.of(),
        Instant.parse("2026-03-11T10:00:00Z"),
        "trace-startup-ok");
    when(configSetDryRunValidator.validateCompatibility(
        argThat(candidate -> candidate.configSetId().equals(configSet.configSetId())
            && candidate.signingSecret().equals("plain-secret")),
        anyString()))
        .thenReturn(result);

    assertThatCode(validator::afterPropertiesSet).doesNotThrowAnyException();

    verify(configSetDryRunValidator).validateCompatibility(
        argThat(candidate -> candidate.configSetId().equals(configSet.configSetId())
            && candidate.signingSecret().equals("plain-secret")),
        anyString());
    verify(compatibilityStateService).record(eq(configSet.configSetId()), eq(result));
  }

  @Test
  void afterPropertiesSetFailsForIncompatibleConfigLoadedThroughRealRepository() {
    ConfigSet configSet = repository.save(activeConfigSet("cs-startup-bad", "plain-secret"));
    ConfigCompatibilityCheckResult result = new ConfigCompatibilityCheckResult(
        false,
        List.of(new ConfigCompatibilityMismatch(
            ConfigCompatibilityMismatchCode.ISSUER_MISMATCH,
            "issuer mismatch",
            "expected",
            "actual")),
        Instant.parse("2026-03-11T10:05:00Z"),
        "trace-startup-bad");
    when(configSetDryRunValidator.validateCompatibility(
        argThat(candidate -> candidate.configSetId().equals(configSet.configSetId())
            && candidate.signingSecret().equals("plain-secret")),
        anyString()))
        .thenReturn(result);

    assertThatThrownBy(validator::afterPropertiesSet)
        .isInstanceOf(JwtStartupValidationException.class)
        .extracting(error -> ((JwtStartupValidationException) error).errorCode())
        .isEqualTo(JwtStartupValidationErrorCode.CONFIG_INCOMPATIBLE.name());

    verify(compatibilityStateService).record(eq(configSet.configSetId()), eq(result));
  }

  private ConfigSet activeConfigSet(String configSetId, String signingSecret) {
    Instant createdAt = Instant.parse("2026-03-11T09:00:00Z");
    Instant updatedAt = Instant.parse("2026-03-11T09:00:00Z");
    return new ConfigSet(
        configSetId,
        "Active " + configSetId,
        "tenant-startup",
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
        ConfigSetStatus.ACTIVE,
        createdAt,
        updatedAt);
  }
}