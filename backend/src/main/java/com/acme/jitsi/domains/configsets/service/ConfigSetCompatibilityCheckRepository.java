package com.acme.jitsi.domains.configsets.service;

import java.util.List;
import java.util.Optional;

public interface ConfigSetCompatibilityCheckRepository {

  ConfigSetCompatibilityCheck save(ConfigSetCompatibilityCheck check);

  Optional<ConfigSetCompatibilityCheck> findLatestByConfigSetId(String configSetId);

  List<ConfigSetCompatibilityCheck> findLatestByConfigSetIds(List<String> configSetIds);
}