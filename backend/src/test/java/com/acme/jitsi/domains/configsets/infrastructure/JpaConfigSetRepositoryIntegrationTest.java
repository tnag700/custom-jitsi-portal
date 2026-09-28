package com.acme.jitsi.domains.configsets.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.acme.jitsi.domains.configsets.service.ConfigSet;
import com.acme.jitsi.domains.configsets.service.ConfigSetEnvironmentType;
import com.acme.jitsi.domains.configsets.service.ConfigSetStatus;
import com.acme.jitsi.domains.configsets.usecase.ActivateConfigSetCommand;
import com.acme.jitsi.domains.configsets.usecase.ActivateConfigSetUseCase;
import com.acme.jitsi.domains.configsets.usecase.RolloutConfigSetCommand;
import com.acme.jitsi.domains.configsets.usecase.RolloutConfigSetUseCase;
import com.acme.jitsi.domains.configsets.usecase.RollbackConfigSetCommand;
import com.acme.jitsi.domains.configsets.usecase.RollbackConfigSetUseCase;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import com.acme.jitsi.shared.JwtTestProperties;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:testdb_configsets_repo;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
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
class JpaConfigSetRepositoryIntegrationTest {

  @Autowired
  private JpaConfigSetRepository repository;

  @Autowired
  private ConfigSetEncryptionService encryptionService;

  @Autowired
  private ConfigSetJpaRepository jpaRepository;

  @Autowired
  private JdbcTemplate jdbcTemplate;

  @Autowired
  private ActivateConfigSetUseCase activate;

  @Autowired
  private RolloutConfigSetUseCase rollout;

  @Autowired
  private RollbackConfigSetUseCase rollback;

  @Test
  void concurrentRolloutsRecordTheActualPredecessorAndRollbackRestoresIt() throws Exception {
    Instant now = Instant.now();
    for (String id : java.util.List.of("a", "b", "c")) {
      repository.save(new ConfigSet(id, id, "tenant-cycle", ConfigSetEnvironmentType.TEST,
          "https://portal.example.test", "jitsi-meet", "HS256", "role", "secret", null, 20, 60,
          "https://meet.example.test/v1", id.equals("a") ? ConfigSetStatus.ACTIVE : ConfigSetStatus.DRAFT,
          now, now));
    }
    var b = CompletableFuture.supplyAsync(() -> rollout.execute(
        new RolloutConfigSetCommand("b", "tenant-cycle", "actor", "trace-b")));
    var c = CompletableFuture.supplyAsync(() -> rollout.execute(
        new RolloutConfigSetCommand("c", "tenant-cycle", "actor", "trace-c")));
    var resultB = b.get(10, TimeUnit.SECONDS);
    var resultC = c.get(10, TimeUnit.SECONDS);
    String activeId = repository.findActiveByTenantIdAndEnvironmentType("tenant-cycle", ConfigSetEnvironmentType.TEST)
        .orElseThrow().configSetId();
    var latest = activeId.equals("b") ? resultB : resultC;
    var first = activeId.equals("b") ? resultC : resultB;
    assertThat(first.previousConfigSetId()).isEqualTo("a");
    assertThat(latest.previousConfigSetId()).isEqualTo(first.configSetId());

    var rolledBack = rollback.execute(new RollbackConfigSetCommand(activeId, "tenant-cycle",
        ConfigSetEnvironmentType.TEST, "actor", "trace-rollback"));
    assertThat(rolledBack.configSetId()).isEqualTo(first.configSetId());
    assertThat(repository.findActiveByTenantIdAndEnvironmentType("tenant-cycle", ConfigSetEnvironmentType.TEST)
        .orElseThrow().configSetId()).isEqualTo(first.configSetId());
  }

  @Test
  void multipleDraftsCanReplaceActiveConfigurationAndSwitchBack() {
    Instant now = Instant.parse("2026-03-10T10:15:30Z");
    repository.save(configSet("a", "A", "tenant-cycle", ConfigSetEnvironmentType.TEST,
        ConfigSetStatus.ACTIVE, "secret", now, now));
    repository.save(configSet("b", "B", "tenant-cycle", ConfigSetEnvironmentType.TEST,
        ConfigSetStatus.DRAFT, "secret", now, now));
    repository.save(configSet("c", "C", "tenant-cycle", ConfigSetEnvironmentType.TEST,
        ConfigSetStatus.DRAFT, "secret", now, now));

    activate.execute(new ActivateConfigSetCommand("b", "tenant-cycle", "actor", "trace"));
    assertThat(repository.findById("a").orElseThrow().status()).isEqualTo(ConfigSetStatus.DRAFT);
    activate.execute(new ActivateConfigSetCommand("a", "tenant-cycle", "actor", "trace"));
    assertThat(repository.findActiveByTenantIdAndEnvironmentType("tenant-cycle", ConfigSetEnvironmentType.TEST)
        .orElseThrow().configSetId()).isEqualTo("a");
    assertThatThrownBy(() -> repository.save(configSet("d", "D", "tenant-cycle", ConfigSetEnvironmentType.TEST,
        ConfigSetStatus.ACTIVE, "secret", now, now)))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
  }

  @BeforeEach
  void setUp() {
    jdbcTemplate.execute("DELETE FROM config_set_compatibility_checks");
    jdbcTemplate.execute("DELETE FROM config_set_rollouts");
    jdbcTemplate.execute("DELETE FROM config_set_audit_events");
    jdbcTemplate.execute("DELETE FROM config_sets");
  }

  @Test
  void saveRoundTripPersistsEncryptedSecretAndReturnsPlaintextDomain() {
    ConfigSet configSet = configSet(
        "cfg-roundtrip-1",
        "Round Trip",
        "tenant-roundtrip",
        ConfigSetEnvironmentType.TEST,
        ConfigSetStatus.ACTIVE,
        "plain-secret",
        Instant.parse("2026-03-10T10:15:30Z"),
        Instant.parse("2026-03-10T10:15:30Z"));

    ConfigSet saved = repository.save(configSet);
    String persistedSecret = persistedSecret(configSet.configSetId());

    assertThat(persistedSecret).isNotBlank().isNotEqualTo("plain-secret");
    assertThat(saved).isEqualTo(configSet);
    assertThat(repository.findById(configSet.configSetId())).contains(configSet);
  }

  @Test
  void updateRoundTripReEncryptsSecretAndKeepsReadPathsBackwardCompatible() {
    ConfigSet original = configSet(
        "cfg-update-1",
        "Config A",
        "tenant-update",
        ConfigSetEnvironmentType.DEV,
        ConfigSetStatus.DRAFT,
        "plain-secret",
        Instant.parse("2026-03-10T10:15:30Z"),
        Instant.parse("2026-03-10T10:15:30Z"));
    repository.save(original);
    String originalEncrypted = persistedSecret(original.configSetId());

    ConfigSet updated = configSet(
        "cfg-update-1",
        "Config A v2",
        "tenant-update",
        ConfigSetEnvironmentType.DEV,
        ConfigSetStatus.ACTIVE,
        "rotated-secret",
        original.createdAt(),
        Instant.parse("2026-03-10T11:15:30Z"));

    ConfigSet saved = repository.save(updated);
    String updatedEncrypted = persistedSecret(updated.configSetId());

    assertThat(updatedEncrypted)
        .isNotBlank()
        .isNotEqualTo(originalEncrypted)
        .isNotEqualTo("rotated-secret");
    assertThat(saved).isEqualTo(updated);
    assertThat(repository.findById(updated.configSetId())).contains(updated);
    assertThat(jdbcTemplate.queryForObject(
      "SELECT COUNT(*) FROM config_sets WHERE config_set_id = ?",
      Integer.class,
      updated.configSetId())).isEqualTo(1);
    assertThat(jdbcTemplate.queryForObject(
      "SELECT created_at FROM config_sets WHERE config_set_id = ?",
      Instant.class,
      updated.configSetId())).isEqualTo(original.createdAt());
    assertThat(repository.findActiveByTenantIdAndEnvironmentType("tenant-update", ConfigSetEnvironmentType.DEV))
        .contains(updated);
    assertThat(repository.findByStatus(ConfigSetStatus.ACTIVE)).contains(updated);
    assertThat(repository.findByTenantId("tenant-update", 0, 10)).containsExactly(updated);
  }

  @Test
  void softDeleteRemainsVisibleInTableButHiddenFromRepositoryQueries() {
    ConfigSet visible = configSet(
        "cfg-visible-1",
        "Visible Config",
        "tenant-soft-delete",
        ConfigSetEnvironmentType.TEST,
        ConfigSetStatus.ACTIVE,
        "visible-secret",
        Instant.parse("2026-03-10T10:15:30Z"),
        Instant.parse("2026-03-10T10:15:30Z"));
    ConfigSet deleted = configSet(
        "cfg-deleted-1",
        "Deleted Config",
        "tenant-soft-delete",
        ConfigSetEnvironmentType.PROD,
        ConfigSetStatus.ACTIVE,
        "deleted-secret",
        Instant.parse("2026-03-10T10:15:30Z"),
        Instant.parse("2026-03-10T10:15:30Z"));

    repository.save(visible);
    repository.save(deleted);

    jpaRepository.deleteById(deleted.configSetId());

    Boolean deletedFlag = jdbcTemplate.queryForObject(
        "SELECT deleted FROM config_sets WHERE config_set_id = ?",
        Boolean.class,
        deleted.configSetId());
    Integer rowCount = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM config_sets WHERE config_set_id = ?",
        Integer.class,
        deleted.configSetId());

    assertThat(deletedFlag).isTrue();
    assertThat(rowCount).isEqualTo(1);
    assertThat(repository.findById(deleted.configSetId())).isEmpty();
    assertThat(repository.findActiveByTenantIdAndEnvironmentType("tenant-soft-delete", ConfigSetEnvironmentType.PROD))
        .isEmpty();
    assertThat(repository.findByStatus(ConfigSetStatus.ACTIVE)).containsExactly(visible);
    assertThat(repository.findByTenantId("tenant-soft-delete", 0, 10)).containsExactly(visible);
    assertThat(repository.searchByTenantId("tenant-soft-delete", null, null, 0, 20).totalElements()).isEqualTo(1);
    assertThat(repository.searchByTenantId("tenant-soft-delete", ConfigSetEnvironmentType.PROD, null, 0, 20).totalElements()).isZero();
    assertThat(repository.searchByTenantId("tenant-soft-delete", ConfigSetEnvironmentType.TEST, ConfigSetStatus.ACTIVE, 0, 20).content())
        .containsExactly(visible);
    assertThat(repository.existsByNameAndTenantId("Deleted Config", "tenant-soft-delete")).isFalse();
  }

    @Test
    void legacyPersistedRowRemainsReadableThroughAllRepositoryReadPaths() {
    insertLegacyRow(
      "cfg-legacy-1",
      "Legacy Config",
      "tenant-legacy",
      ConfigSetEnvironmentType.PROD,
      ConfigSetStatus.ACTIVE,
      "legacy-secret",
      Instant.parse("2026-03-10T09:15:30Z"),
      Instant.parse("2026-03-10T09:45:30Z"));

    assertThat(repository.findById("cfg-legacy-1"))
      .get()
      .extracting(ConfigSet::signingSecret)
      .isEqualTo("legacy-secret");
    assertThat(repository.findActiveByTenantIdAndEnvironmentType("tenant-legacy", ConfigSetEnvironmentType.PROD))
      .get()
      .extracting(ConfigSet::signingSecret)
      .isEqualTo("legacy-secret");
    assertThat(repository.findByStatus(ConfigSetStatus.ACTIVE))
      .filteredOn(configSet -> configSet.configSetId().equals("cfg-legacy-1"))
      .singleElement()
      .extracting(ConfigSet::signingSecret)
      .isEqualTo("legacy-secret");
    assertThat(repository.findByTenantId("tenant-legacy", 0, 10))
      .singleElement()
      .extracting(ConfigSet::signingSecret)
      .isEqualTo("legacy-secret");
    }

  private String persistedSecret(String configSetId) {
    return jdbcTemplate.queryForObject(
        "SELECT signing_secret_encrypted FROM config_sets WHERE config_set_id = ?",
        String.class,
        configSetId);
  }

  private void insertLegacyRow(
      String configSetId,
      String name,
      String tenantId,
      ConfigSetEnvironmentType environmentType,
      ConfigSetStatus status,
      String signingSecret,
      Instant createdAt,
      Instant updatedAt) {
    jdbcTemplate.update(
        """
        INSERT INTO config_sets (
          config_set_id,
          name,
          tenant_id,
          environment_type,
          issuer,
          audience,
          algorithm,
          role_claim,
          signing_secret_encrypted,
          jwks_uri,
          access_ttl_minutes,
          refresh_ttl_minutes,
          meetings_service_url,
          status,
          created_at,
          updated_at,
          deleted
        )
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        configSetId,
        name,
        tenantId,
        environmentType.name(),
        "https://issuer.example.test/" + configSetId,
        "audience-" + configSetId,
        "HS256",
        "role",
        encryptionService.encrypt(signingSecret),
        "https://jwks.example.test/" + configSetId,
        20,
        120,
        "https://meet.example.test/" + configSetId,
        status.name(),
        createdAt.atOffset(java.time.ZoneOffset.UTC),
        updatedAt.atOffset(java.time.ZoneOffset.UTC),
        false);
  }

  private ConfigSet configSet(
      String configSetId,
      String name,
      String tenantId,
      ConfigSetEnvironmentType environmentType,
      ConfigSetStatus status,
      String signingSecret,
      Instant createdAt,
      Instant updatedAt) {
    return new ConfigSet(
        configSetId,
        name,
        tenantId,
        environmentType,
        "https://issuer.example.test/" + configSetId,
        "audience-" + configSetId,
        "HS256",
        "role",
        signingSecret,
        "https://jwks.example.test/" + configSetId,
        20,
        120,
        "https://meet.example.test/" + configSetId,
        status,
        createdAt,
        updatedAt);
  }
}
