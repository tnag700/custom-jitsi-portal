package com.acme.jitsi.domains.configsets.service;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ConfigSetCompatibilityStateService {

  private final ConfigSetCompatibilityCheckRepository compatibilityCheckRepository;
  private final ConfigSetRepository configSetRepository;

  public ConfigSetCompatibilityStateService(
      ConfigSetCompatibilityCheckRepository compatibilityCheckRepository,
      ConfigSetRepository configSetRepository) {
    this.compatibilityCheckRepository = compatibilityCheckRepository;
    this.configSetRepository = configSetRepository;
  }

  public ConfigSetCompatibilityCheck record(String configSetId, ConfigCompatibilityCheckResult result) {
    return compatibilityCheckRepository.save(new ConfigSetCompatibilityCheck(
        UUID.randomUUID().toString(),
        configSetId,
        result.compatible(),
        result.mismatches().stream().map(mismatch -> mismatch.code().name()).toList(),
        result.mismatches().stream().map(ConfigCompatibilityMismatch::message).reduce((left, right) -> left + "; " + right).orElse(""),
        result.checkedAt(),
        result.traceId()));
  }

  public Optional<ConfigSetCompatibilityCheck> findLatestByConfigSetId(String configSetId) {
    return compatibilityCheckRepository.findLatestByConfigSetId(configSetId);
  }

  public Optional<ConfigSetCompatibilityCheck> findLatestIncompatibleActive() {
    List<String> activeConfigSetIds = configSetRepository.findByStatus(ConfigSetStatus.ACTIVE)
      .stream()
      .map(ConfigSet::configSetId)
      .toList();
    return compatibilityCheckRepository.findLatestByConfigSetIds(activeConfigSetIds)
      .stream()
      .filter(check -> !check.compatible())
        .max(Comparator.comparing(ConfigSetCompatibilityCheck::checkedAt));
  }
}