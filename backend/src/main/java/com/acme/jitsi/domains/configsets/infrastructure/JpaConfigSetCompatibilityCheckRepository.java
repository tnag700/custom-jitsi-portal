package com.acme.jitsi.domains.configsets.infrastructure;

import com.acme.jitsi.domains.configsets.service.ConfigSetCompatibilityCheck;
import com.acme.jitsi.domains.configsets.service.ConfigSetCompatibilityCheckRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
class JpaConfigSetCompatibilityCheckRepository implements ConfigSetCompatibilityCheckRepository {

  private final ConfigSetCompatibilityCheckJpaRepository jpaRepository;

  JpaConfigSetCompatibilityCheckRepository(ConfigSetCompatibilityCheckJpaRepository jpaRepository) {
    this.jpaRepository = jpaRepository;
  }

  @Override
  public ConfigSetCompatibilityCheck save(ConfigSetCompatibilityCheck check) {
    return jpaRepository.save(new ConfigSetCompatibilityCheckEntity(check)).toDomain();
  }

  @Override
  public Optional<ConfigSetCompatibilityCheck> findLatestByConfigSetId(String configSetId) {
    return jpaRepository.findTopByConfigSetIdOrderByCheckedAtDesc(configSetId)
        .map(ConfigSetCompatibilityCheckEntity::toDomain);
  }

  @Override
  public List<ConfigSetCompatibilityCheck> findLatestByConfigSetIds(List<String> configSetIds) {
    if (configSetIds == null || configSetIds.isEmpty()) {
      return List.of();
    }
    return jpaRepository.findLatestByConfigSetIds(configSetIds)
        .stream()
        .map(ConfigSetCompatibilityCheckEntity::toDomain)
        .toList();
  }
}