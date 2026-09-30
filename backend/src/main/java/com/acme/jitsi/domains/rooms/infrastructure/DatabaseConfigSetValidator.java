package com.acme.jitsi.domains.rooms.infrastructure;

import com.acme.jitsi.domains.configsets.service.ConfigSetNotFoundException;
import com.acme.jitsi.domains.configsets.service.ConfigSetService;
import com.acme.jitsi.domains.configsets.service.ConfigSetStatus;
import com.acme.jitsi.domains.rooms.service.ConfigSetValidator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.features.config-sets-from-db", havingValue = "true")
class DatabaseConfigSetValidator implements ConfigSetValidator {

  private final ConfigSetService configSetService;

  DatabaseConfigSetValidator(ConfigSetService configSetService) {
    this.configSetService = configSetService;
  }

  @Override
  public boolean isValid(String configSetId, String tenantId) {
    if (configSetId == null || configSetId.isBlank() || tenantId == null || tenantId.isBlank()) {
      return false;
    }
    try {
      var configSet = configSetService.getById(configSetId);
      return tenantId.equals(configSet.tenantId())
          && (configSet.status() == ConfigSetStatus.ACTIVE
              || configSet.status() == ConfigSetStatus.DRAFT);
    } catch (ConfigSetNotFoundException exception) {
      return false;
    }
  }
}
