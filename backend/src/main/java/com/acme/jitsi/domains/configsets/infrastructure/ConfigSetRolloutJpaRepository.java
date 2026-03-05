package com.acme.jitsi.domains.configsets.infrastructure;

import com.acme.jitsi.domains.configsets.service.ConfigSetEnvironmentType;
import com.acme.jitsi.domains.configsets.service.RolloutStatus;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface ConfigSetRolloutJpaRepository extends JpaRepository<ConfigSetRolloutEntity, String> {

  Optional<ConfigSetRolloutEntity> findTopByTenantIdAndEnvironmentTypeOrderByStartedAtDesc(
      String tenantId,
      ConfigSetEnvironmentType environmentType);

  Optional<ConfigSetRolloutEntity> findTopByTenantIdAndEnvironmentTypeAndStatusOrderByStartedAtDesc(
      String tenantId,
      ConfigSetEnvironmentType environmentType,
      RolloutStatus status);
}