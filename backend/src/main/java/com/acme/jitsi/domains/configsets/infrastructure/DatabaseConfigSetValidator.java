package com.acme.jitsi.domains.configsets.infrastructure;

import com.acme.jitsi.domains.configsets.service.ConfigSetRepository;
import com.acme.jitsi.domains.configsets.service.ConfigSetStatus;
import com.acme.jitsi.domains.rooms.service.ConfigSetValidator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.features.config-sets-from-db", havingValue = "true")
class DatabaseConfigSetValidator implements ConfigSetValidator {

  private final ConfigSetRepository configSetRepository;

  DatabaseConfigSetValidator(ConfigSetRepository configSetRepository) {
    this.configSetRepository = configSetRepository;
  }

  @Override
  public boolean isValid(String configSetId) {
    if (configSetId == null || configSetId.isBlank()) {
      return false;
    }
    return configSetRepository.findById(configSetId)
        .map(configSet -> configSet.status() == ConfigSetStatus.ACTIVE || configSet.status() == ConfigSetStatus.DRAFT)
        .orElse(false);
  }
}