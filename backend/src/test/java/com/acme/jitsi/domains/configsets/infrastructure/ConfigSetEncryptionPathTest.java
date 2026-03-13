package com.acme.jitsi.domains.configsets.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.acme.jitsi.domains.configsets.service.ConfigSet;
import com.acme.jitsi.domains.configsets.service.ConfigSetEnvironmentType;
import com.acme.jitsi.domains.configsets.service.ConfigSetInvalidDataException;
import com.acme.jitsi.domains.configsets.service.ConfigSetStatus;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.data.domain.PageImpl;

class ConfigSetEncryptionPathTest {

  @ParameterizedTest
  @MethodSource("rawKeys")
  void encryptDecryptRoundTripsForRawKeys(String rawKey) {
    ConfigSetEncryptionService encryptionService = new ConfigSetEncryptionService(rawKey);

    String encrypted = encryptionService.encrypt("signing-secret");

    assertThat(encrypted).isNotBlank().isNotEqualTo("signing-secret");
    assertThat(encryptionService.decrypt(encrypted)).isEqualTo("signing-secret");
  }

  @ParameterizedTest
  @MethodSource("base64Keys")
  void encryptDecryptRoundTripsForBase64Keys(String base64Key) {
    ConfigSetEncryptionService encryptionService = new ConfigSetEncryptionService(base64Key);

    String encrypted = encryptionService.encrypt("signing-secret");

    assertThat(encrypted).isNotBlank().isNotEqualTo("signing-secret");
    assertThat(encryptionService.decrypt(encrypted)).isEqualTo("signing-secret");
  }

  @Test
  void encryptAndDecryptReturnNullForNullValues() {
    ConfigSetEncryptionService encryptionService = new ConfigSetEncryptionService(rawKey(16));

    assertThat(encryptionService.encrypt(null)).isNull();
    assertThat(encryptionService.decrypt(null)).isNull();
  }

  @Test
  void encryptRejectsInvalidKeyLength() {
    ConfigSetEncryptionService encryptionService = new ConfigSetEncryptionService("short-key");

    assertThatThrownBy(() -> encryptionService.encrypt("signing-secret"))
        .isInstanceOf(ConfigSetInvalidDataException.class)
        .hasMessageContaining("APP_CONFIG_SETS_ENCRYPTION_KEY must be 16, 24, or 32 bytes");
  }

  @Test
  void decryptRejectsNonBase64Payload() {
    ConfigSetEncryptionService encryptionService = new ConfigSetEncryptionService(rawKey(16));

    assertThatThrownBy(() -> encryptionService.decrypt("%%%not-base64%%%"))
        .isInstanceOf(ConfigSetInvalidDataException.class)
        .hasMessage("Failed to decrypt signingSecret");
  }

  @Test
  void decryptRejectsTooShortPayload() {
    ConfigSetEncryptionService encryptionService = new ConfigSetEncryptionService(rawKey(16));
    String invalidPayload = Base64.getEncoder().encodeToString(new byte[12]);

    assertThatThrownBy(() -> encryptionService.decrypt(invalidPayload))
        .isInstanceOf(ConfigSetInvalidDataException.class)
        .hasMessage("Encrypted signingSecret payload is invalid");
  }

  @Test
  void translatorEncryptsOnNewEntityAndDecryptsOnToDomain() throws Exception {
    ConfigSetEncryptionService encryptionService = new ConfigSetEncryptionService(rawKey(16));
    ConfigSetPersistenceTranslator translator = new ConfigSetPersistenceTranslator(encryptionService);
    ConfigSet configSet = sampleConfigSet("plain-secret");

    ConfigSetEntity entity = translator.toNewEntity(configSet);
    String encryptedSecret = readSigningSecretEncrypted(entity);
    ConfigSet restored = translator.toDomain(entity);

    assertThat(encryptedSecret).isNotBlank().isNotEqualTo("plain-secret");
    assertThat(restored.signingSecret()).isEqualTo("plain-secret");
    assertThat(restored).isEqualTo(configSet);
  }

  @Test
  void jpaRepositorySaveEncryptsOnWriteAndDecryptsReturnedDomain() throws Exception {
    ConfigSetJpaRepository jpaRepository = mock(ConfigSetJpaRepository.class);
    ConfigSetEncryptionService encryptionService = new ConfigSetEncryptionService(rawKey(16));
    ConfigSetPersistenceTranslator translator = new ConfigSetPersistenceTranslator(encryptionService);
    JpaConfigSetRepository repository = new JpaConfigSetRepository(jpaRepository, translator);
    ConfigSet configSet = sampleConfigSet("plain-secret");
    AtomicReference<ConfigSetEntity> savedEntityRef = new AtomicReference<>();

    when(jpaRepository.save(any(ConfigSetEntity.class))).thenAnswer(invocation -> {
      ConfigSetEntity entity = invocation.getArgument(0);
      savedEntityRef.set(entity);
      return entity;
    });

    ConfigSet saved = repository.save(configSet);
    ConfigSetEntity persistedEntity = savedEntityRef.get();

    assertThat(readSigningSecretEncrypted(persistedEntity)).isNotBlank().isNotEqualTo("plain-secret");
    assertThat(saved.signingSecret()).isEqualTo("plain-secret");
  }

  @Test
  void jpaRepositorySaveUpdatesExistingEntityAndReEncryptsSecret() throws Exception {
    ConfigSetJpaRepository jpaRepository = mock(ConfigSetJpaRepository.class);
    ConfigSetEncryptionService encryptionService = new ConfigSetEncryptionService(rawKey(16));
    ConfigSetPersistenceTranslator translator = new ConfigSetPersistenceTranslator(encryptionService);
    JpaConfigSetRepository repository = new JpaConfigSetRepository(jpaRepository, translator);
    ConfigSet original = sampleConfigSet("plain-secret");
    ConfigSet updated = updatedConfigSet("rotated-secret");
    ConfigSetEntity existingEntity = translator.toNewEntity(original);
    String originalEncryptedSecret = readSigningSecretEncrypted(existingEntity);
    AtomicReference<ConfigSetEntity> savedEntityRef = new AtomicReference<>();

    when(jpaRepository.save(any(ConfigSetEntity.class))).thenAnswer(invocation -> {
      ConfigSetEntity entity = invocation.getArgument(0);
      savedEntityRef.set(entity);
      return entity;
    });

    ConfigSet saved = repository.save(updated);
    ConfigSetEntity persistedEntity = savedEntityRef.get();

    assertThat(persistedEntity).isNotSameAs(existingEntity);
    assertThat(readSigningSecretEncrypted(persistedEntity))
        .isNotBlank()
        .isNotEqualTo(originalEncryptedSecret)
        .isNotEqualTo("rotated-secret");
    assertThat(saved).isEqualTo(updated);
  }

  @Test
  void jpaRepositoryFindByIdDecryptsPersistedEntity() {
    ConfigSetEncryptionService encryptionService = new ConfigSetEncryptionService(rawKey(16));
    ConfigSetPersistenceTranslator translator = new ConfigSetPersistenceTranslator(encryptionService);
    ConfigSet configSet = sampleConfigSet("plain-secret");
    ConfigSetEntity storedEntity = translator.toNewEntity(configSet);
    ConfigSetJpaRepository jpaRepository = mock(ConfigSetJpaRepository.class);
    JpaConfigSetRepository repository = new JpaConfigSetRepository(jpaRepository, translator);

    when(jpaRepository.findById(configSet.configSetId())).thenReturn(Optional.of(storedEntity));

    Optional<ConfigSet> found = repository.findById(configSet.configSetId());

    assertThat(found).contains(configSet);
  }

  @Test
  void jpaRepositoryFindActiveDecryptsPersistedEntity() {
    ConfigSetEncryptionService encryptionService = new ConfigSetEncryptionService(rawKey(16));
    ConfigSetPersistenceTranslator translator = new ConfigSetPersistenceTranslator(encryptionService);
    ConfigSet configSet = sampleConfigSet("plain-secret");
    ConfigSetEntity storedEntity = translator.toNewEntity(configSet);
    ConfigSetJpaRepository jpaRepository = mock(ConfigSetJpaRepository.class);
    JpaConfigSetRepository repository = new JpaConfigSetRepository(jpaRepository, translator);

    when(jpaRepository.findByTenantIdAndEnvironmentTypeAndStatus(
        configSet.tenantId(),
        configSet.environmentType(),
        ConfigSetStatus.ACTIVE)).thenReturn(Optional.of(storedEntity));

    Optional<ConfigSet> found = repository.findActiveByTenantIdAndEnvironmentType(
        configSet.tenantId(),
        configSet.environmentType());

    assertThat(found).contains(configSet);
  }

  @Test
  void jpaRepositoryFindByStatusDecryptsEachPersistedEntity() {
    ConfigSetEncryptionService encryptionService = new ConfigSetEncryptionService(rawKey(16));
    ConfigSetPersistenceTranslator translator = new ConfigSetPersistenceTranslator(encryptionService);
    ConfigSet active = sampleConfigSet("plain-secret");
    ConfigSet inactive = updatedConfigSet("rotated-secret");
    ConfigSetJpaRepository jpaRepository = mock(ConfigSetJpaRepository.class);
    JpaConfigSetRepository repository = new JpaConfigSetRepository(jpaRepository, translator);

    when(jpaRepository.findByStatus(ConfigSetStatus.ACTIVE))
        .thenReturn(List.of(translator.toNewEntity(active)));
    when(jpaRepository.findByStatus(ConfigSetStatus.INACTIVE))
        .thenReturn(List.of(translator.toNewEntity(inactive)));

    assertThat(repository.findByStatus(ConfigSetStatus.ACTIVE)).containsExactly(active);
    assertThat(repository.findByStatus(ConfigSetStatus.INACTIVE)).containsExactly(inactive);
  }

  @Test
  void jpaRepositoryFindByTenantIdDecryptsPagedPersistedEntities() {
    ConfigSetEncryptionService encryptionService = new ConfigSetEncryptionService(rawKey(16));
    ConfigSetPersistenceTranslator translator = new ConfigSetPersistenceTranslator(encryptionService);
    ConfigSet first = sampleConfigSet("plain-secret");
    ConfigSet second = updatedConfigSet("rotated-secret");
    ConfigSetJpaRepository jpaRepository = mock(ConfigSetJpaRepository.class);
    JpaConfigSetRepository repository = new JpaConfigSetRepository(jpaRepository, translator);

    when(jpaRepository.findByTenantIdOrderByCreatedAtDesc(first.tenantId(), org.springframework.data.domain.PageRequest.of(0, 2)))
        .thenReturn(new PageImpl<>(List.of(translator.toNewEntity(first), translator.toNewEntity(second))));

    assertThat(repository.findByTenantId(first.tenantId(), 0, 2)).containsExactly(first, second);
  }

  private static Stream<String> rawKeys() {
    return Stream.of(rawKey(16), rawKey(24), rawKey(32));
  }

  private static Stream<String> base64Keys() {
    return Stream.of(16, 24, 32)
        .map(length -> Base64.getEncoder().encodeToString(repeatedByteArray(length)));
  }

  private static byte[] repeatedByteArray(int length) {
    byte[] bytes = new byte[length];
    for (int index = 0; index < length; index++) {
      bytes[index] = (byte) ('a' + (index % 26));
    }
    return bytes;
  }

  private static String rawKey(int length) {
    return "r".repeat(length - 1) + "!";
  }

  private ConfigSet sampleConfigSet(String signingSecret) {
    Instant createdAt = Instant.parse("2026-03-10T10:15:30Z");
    Instant updatedAt = Instant.parse("2026-03-10T11:15:30Z");
    return new ConfigSet(
        "cfg-1",
        "Primary config",
        "tenant-1",
        ConfigSetEnvironmentType.TEST,
        "issuer-a",
        "audience-a",
        "HS256",
        "role",
        signingSecret,
        "https://example.test/jwks.json",
        15,
        60,
        "https://meetings.test",
        ConfigSetStatus.ACTIVE,
        createdAt,
        updatedAt);
  }

        private ConfigSet updatedConfigSet(String signingSecret) {
          Instant createdAt = Instant.parse("2026-03-10T10:15:30Z");
          Instant updatedAt = Instant.parse("2026-03-10T12:15:30Z");
          return new ConfigSet(
          "cfg-1",
          "Primary config v2",
          "tenant-1",
          ConfigSetEnvironmentType.PROD,
          "issuer-b",
          "audience-b",
          "HS512",
          "role-v2",
          signingSecret,
          "https://example.test/next-jwks.json",
          30,
          120,
          "https://meetings-next.test",
          ConfigSetStatus.INACTIVE,
          createdAt,
          updatedAt);
        }

  private String readSigningSecretEncrypted(ConfigSetEntity entity) throws Exception {
    Field field = ConfigSetEntity.class.getDeclaredField("signingSecretEncrypted");
    field.setAccessible(true);
    Object value = field.get(entity);
    return value == null ? null : value.toString();
  }
}