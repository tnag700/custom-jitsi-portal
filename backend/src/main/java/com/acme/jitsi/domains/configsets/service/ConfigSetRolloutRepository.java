package com.acme.jitsi.domains.configsets.service;

import java.util.Optional;

public interface ConfigSetRolloutRepository {

  ConfigSetRollout save(ConfigSetRollout rollout);

  Optional<ConfigSetRollout> findById(String rolloutId);

  Optional<ConfigSetRollout> findLatestByTenantIdAndEnvironmentType(
      String tenantId,
      ConfigSetEnvironmentType environmentType);

  Optional<ConfigSetRollout> findLatestSucceededByTenantIdAndEnvironmentType(
      String tenantId,
      ConfigSetEnvironmentType environmentType);
}