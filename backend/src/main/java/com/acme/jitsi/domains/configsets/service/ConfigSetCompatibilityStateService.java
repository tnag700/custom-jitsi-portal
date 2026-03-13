package com.acme.jitsi.domains.configsets.service;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.acme.jitsi.shared.observability.FlowObservationFacade;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ConfigSetCompatibilityStateService {

  private final ConfigSetCompatibilityCheckRepository compatibilityCheckRepository;
  private final ConfigSetRepository configSetRepository;
  private final FlowObservationFacade flowObservationFacade;

  public ConfigSetCompatibilityStateService(
      ConfigSetCompatibilityCheckRepository compatibilityCheckRepository,
      ConfigSetRepository configSetRepository) {
    this(
        compatibilityCheckRepository,
        configSetRepository,
        FlowObservationFacade.noop());
  }

  @Autowired
  public ConfigSetCompatibilityStateService(
      ConfigSetCompatibilityCheckRepository compatibilityCheckRepository,
      ConfigSetRepository configSetRepository,
      FlowObservationFacade flowObservationFacade) {
    this.compatibilityCheckRepository = compatibilityCheckRepository;
    this.configSetRepository = configSetRepository;
    this.flowObservationFacade = flowObservationFacade;
  }

  public ConfigSetCompatibilityCheck record(String configSetId, ConfigCompatibilityCheckResult result) {
    return flowObservationFacade.observe("config.compatibility.check", observation -> {
      observation.stage("compatibility_record")
          .compatibility(result.compatible() ? "compatible" : "incompatible")
          .outcome(result.compatible() ? "success" : "compatibility_failure");
      return compatibilityCheckRepository.save(new ConfigSetCompatibilityCheck(
          UUID.randomUUID().toString(),
          configSetId,
          result.compatible(),
          result.mismatches().stream().map(mismatch -> mismatch.code().name()).toList(),
          result.mismatches().stream().map(ConfigCompatibilityMismatch::message).reduce((left, right) -> left + "; " + right).orElse(""),
          result.checkedAt(),
          result.traceId()));
    });
  }

  public Optional<ConfigSetCompatibilityCheck> findLatestByConfigSetId(String configSetId) {
    return compatibilityCheckRepository.findLatestByConfigSetId(configSetId);
  }

  public Optional<ConfigSetCompatibilityCheck> findLatestIncompatibleActive() {
    return flowObservationFacade.observe("config.compatibility.check", observation -> {
      observation.stage("compatibility_lookup");
      List<String> activeConfigSetIds = configSetRepository.findByStatus(ConfigSetStatus.ACTIVE)
        .stream()
        .map(ConfigSet::configSetId)
        .toList();
      Optional<ConfigSetCompatibilityCheck> result = compatibilityCheckRepository.findLatestByConfigSetIds(activeConfigSetIds)
        .stream()
        .filter(check -> !check.compatible())
          .max(Comparator.comparing(ConfigSetCompatibilityCheck::checkedAt));
      observation.compatibility(result.isPresent() ? "incompatible" : "compatible")
          .outcome(result.isPresent() ? "compatibility_failure" : "success");
      return result;
    });
  }
}