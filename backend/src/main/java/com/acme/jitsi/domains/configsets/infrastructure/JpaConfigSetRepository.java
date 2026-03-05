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
  private final ConfigSetEncryptionService encryptionService;

  JpaConfigSetRepository(
      ConfigSetJpaRepository jpaRepository,
      ConfigSetEncryptionService encryptionService) {
    this.jpaRepository = jpaRepository;
    this.encryptionService = encryptionService;
  }

  @Override
  public ConfigSet save(ConfigSet configSet) {
    ConfigSetEntity entity = jpaRepository.findById(configSet.configSetId())
        .map(existing -> {
          existing.updateFrom(configSet, encryptionService);
          return existing;
        })
        .orElseGet(() -> new ConfigSetEntity(configSet, encryptionService));

    ConfigSetEntity saved = jpaRepository.save(entity);
    return saved.toDomain(encryptionService);
  }

  @Override
  public Optional<ConfigSet> findById(String configSetId) {
    return jpaRepository.findById(configSetId)
        .map(entity -> entity.toDomain(encryptionService));
  }

  @Override
  public Optional<ConfigSet> findActiveByTenantIdAndEnvironmentType(
      String tenantId,
      ConfigSetEnvironmentType environmentType) {
    return jpaRepository.findByTenantIdAndEnvironmentTypeAndStatus(
            tenantId,
            environmentType,
            ConfigSetStatus.ACTIVE)
        .map(entity -> entity.toDomain(encryptionService));
  }

        @Override
        public List<ConfigSet> findByStatus(ConfigSetStatus status) {
          return jpaRepository.findByStatus(status)
          .stream()
          .map(entity -> entity.toDomain(encryptionService))
          .toList();
        }

  @Override
  public List<ConfigSet> findByTenantId(String tenantId, int page, int size) {
    return jpaRepository.findByTenantIdOrderByCreatedAtDesc(tenantId, PageRequest.of(page, size))
        .stream()
        .map(entity -> entity.toDomain(encryptionService))
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