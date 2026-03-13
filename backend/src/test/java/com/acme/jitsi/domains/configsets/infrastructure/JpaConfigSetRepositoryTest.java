package com.acme.jitsi.domains.configsets.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.acme.jitsi.domains.configsets.service.ConfigSet;
import com.acme.jitsi.domains.configsets.service.ConfigSetEnvironmentType;
import com.acme.jitsi.domains.configsets.service.ConfigSetStatus;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JpaConfigSetRepositoryTest {

  @Mock
  private ConfigSetJpaRepository jpaRepository;

  @Mock
  private ConfigSetPersistenceTranslator translator;

  private JpaConfigSetRepository repository;

  @BeforeEach
  void setUp() {
    repository = new JpaConfigSetRepository(jpaRepository, translator);
  }

  @Test
  void savePersistsTranslatedEntityWithoutPreRead() {
    ConfigSet configSet = new ConfigSet(
        "cfg-1",
        "Config 1",
        "tenant-1",
        ConfigSetEnvironmentType.TEST,
        "https://issuer.example.test/cfg-1",
        "audience-cfg-1",
        "HS256",
        "role",
        "plain-secret",
        "https://jwks.example.test/cfg-1",
        20,
        120,
        "https://meet.example.test/cfg-1",
        ConfigSetStatus.DRAFT,
        Instant.parse("2026-03-11T09:00:00Z"),
        Instant.parse("2026-03-11T09:30:00Z"));
    ConfigSetEntity entity = new ConfigSetEntity();
    when(translator.toNewEntity(configSet)).thenReturn(entity);
    when(jpaRepository.save(entity)).thenReturn(entity);
    when(translator.toDomain(entity)).thenReturn(configSet);

    ConfigSet saved = repository.save(configSet);

    assertThat(saved).isEqualTo(configSet);
    verify(translator).toNewEntity(configSet);
    verify(jpaRepository).save(entity);
  }
}