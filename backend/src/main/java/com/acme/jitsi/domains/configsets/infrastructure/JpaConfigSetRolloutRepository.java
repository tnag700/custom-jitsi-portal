package com.acme.jitsi.domains.configsets.infrastructure;

import com.acme.jitsi.domains.configsets.service.ConfigSetEnvironmentType;
import com.acme.jitsi.domains.configsets.service.ConfigSetRollout;
import com.acme.jitsi.domains.configsets.service.ConfigSetRolloutRepository;
import com.acme.jitsi.domains.configsets.service.RolloutStatus;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
class JpaConfigSetRolloutRepository implements ConfigSetRolloutRepository {

  private final ConfigSetRolloutJpaRepository jpaRepository;

  JpaConfigSetRolloutRepository(ConfigSetRolloutJpaRepository jpaRepository) {
    this.jpaRepository = jpaRepository;
  }

  @Override
  public ConfigSetRollout save(ConfigSetRollout rollout) {
    ConfigSetRolloutEntity entity = jpaRepository.findById(rollout.rolloutId())
        .map(existing -> {
          existing.updateFrom(rollout);
          return existing;
        })
        .orElseGet(() -> new ConfigSetRolloutEntity(rollout));
    return jpaRepository.save(entity).toDomain();
  }

  @Override
  public Optional<ConfigSetRollout> findById(String rolloutId) {
    return jpaRepository.findById(rolloutId).map(ConfigSetRolloutEntity::toDomain);
  }

  @Override
  public Optional<ConfigSetRollout> findLatestByTenantIdAndEnvironmentType(
      String tenantId,
      ConfigSetEnvironmentType environmentType) {
    return jpaRepository.findTopByTenantIdAndEnvironmentTypeOrderByStartedAtDesc(tenantId, environmentType)
        .map(ConfigSetRolloutEntity::toDomain);
  }

  @Override
  public Optional<ConfigSetRollout> findLatestSucceededByTenantIdAndEnvironmentType(
      String tenantId,
      ConfigSetEnvironmentType environmentType) {
    return jpaRepository.findTopByTenantIdAndEnvironmentTypeAndStatusOrderByStartedAtDesc(
            tenantId,
            environmentType,
            RolloutStatus.SUCCEEDED)
        .map(ConfigSetRolloutEntity::toDomain);
  }
}