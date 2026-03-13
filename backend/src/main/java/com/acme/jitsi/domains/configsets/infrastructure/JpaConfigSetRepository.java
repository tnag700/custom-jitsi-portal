package com.acme.jitsi.domains.configsets.infrastructure;

import com.acme.jitsi.domains.configsets.service.ConfigSet;
import com.acme.jitsi.domains.configsets.service.ConfigSetEnvironmentType;
import com.acme.jitsi.domains.configsets.service.ConfigSetRepository;
import com.acme.jitsi.domains.configsets.service.ConfigSetStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

@Repository
class JpaConfigSetRepository implements ConfigSetRepository {

  private final ConfigSetJpaRepository jpaRepository;
  private final ConfigSetPersistenceTranslator translator;

  JpaConfigSetRepository(
      ConfigSetJpaRepository jpaRepository,
      ConfigSetPersistenceTranslator translator) {
    this.jpaRepository = jpaRepository;
    this.translator = translator;
  }

  @Override
  public ConfigSet save(ConfigSet configSet) {
    ConfigSetEntity saved = jpaRepository.save(translator.toNewEntity(configSet));
    return translator.toDomain(saved);
  }

  @Override
  public Optional<ConfigSet> findById(String configSetId) {
    return jpaRepository.findById(configSetId)
        .map(translator::toDomain);
  }

  @Override
  public Optional<ConfigSet> findActiveByTenantIdAndEnvironmentType(
      String tenantId,
      ConfigSetEnvironmentType environmentType) {
    return jpaRepository.findByTenantIdAndEnvironmentTypeAndStatus(
            tenantId,
            environmentType,
            ConfigSetStatus.ACTIVE)
        .map(translator::toDomain);
  }

      @Override
      public List<ConfigSet> findByStatus(ConfigSetStatus status) {
        return jpaRepository.findByStatus(status)
        .stream()
        .map(translator::toDomain)
        .toList();
      }

  @Override
  public List<ConfigSet> findByTenantId(String tenantId, int page, int size) {
    return jpaRepository.findByTenantIdOrderByCreatedAtDesc(tenantId, PageRequest.of(page, size))
        .stream()
        .map(translator::toDomain)
        .toList();
  }

  @Override
  public long countByTenantId(String tenantId) {
    return jpaRepository.countByTenantId(tenantId);
  }

  @Override
  public boolean existsByNameAndTenantId(String name, String tenantId) {
    return jpaRepository.existsByNameAndTenantId(name, tenantId);
  }

  @Override
  public boolean existsByNameAndTenantIdAndConfigSetIdNot(String name, String tenantId, String configSetId) {
    return jpaRepository.existsByNameAndTenantIdAndConfigSetIdNot(name, tenantId, configSetId);
  }
}