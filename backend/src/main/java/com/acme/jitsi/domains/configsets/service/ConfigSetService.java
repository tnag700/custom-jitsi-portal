package com.acme.jitsi.domains.configsets.service;

import org.springframework.stereotype.Service;

@Service
public class ConfigSetService {

  private final ConfigSetRepository configSetRepository;

  public ConfigSetService(ConfigSetRepository configSetRepository) {
    this.configSetRepository = configSetRepository;
  }

  public ConfigSet getById(String configSetId) {
    return configSetRepository.findById(configSetId)
        .orElseThrow(() -> new ConfigSetNotFoundException(configSetId));
  }

  public ConfigSetRepository.ConfigSetPage searchByTenant(
      String tenantId, ConfigSetEnvironmentType environmentType, ConfigSetStatus status,
      int page, int size) {
    if (tenantId == null || tenantId.isBlank()) {
      throw new ConfigSetInvalidDataException("Tenant ID is required");
    }
    if (page < 0) {
      throw new ConfigSetInvalidDataException("Page must be greater than or equal to 0");
    }
    if (size <= 0) {
      throw new ConfigSetInvalidDataException("Size must be greater than 0");
    }
    return configSetRepository.searchByTenantId(tenantId, environmentType, status, page, size);
  }

  public ConfigSet getActiveForEnvironment(String tenantId, ConfigSetEnvironmentType environmentType) {
    return configSetRepository.findActiveByTenantIdAndEnvironmentType(tenantId, environmentType)
        .orElseThrow(() -> new ConfigSetNotFoundException(
            "No active config-set for tenant '%s' and environment '%s'"
                .formatted(tenantId, environmentType)));
  }
}
