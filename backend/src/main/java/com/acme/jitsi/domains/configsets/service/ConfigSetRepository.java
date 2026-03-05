package com.acme.jitsi.domains.configsets.service;

import java.util.List;
import java.util.Optional;

public interface ConfigSetRepository {

  ConfigSet save(ConfigSet configSet);

  Optional<ConfigSet> findById(String configSetId);

  Optional<ConfigSet> findActiveByTenantIdAndEnvironmentType(
      String tenantId,
      ConfigSetEnvironmentType environmentType);

  List<ConfigSet> findByStatus(ConfigSetStatus status);

  List<ConfigSet> findByTenantId(String tenantId, int page, int size);

  long countByTenantId(String tenantId);

  boolean existsByNameAndTenantId(String name, String tenantId);

  boolean existsByNameAndTenantIdAndConfigSetIdNot(String name, String tenantId, String configSetId);
}