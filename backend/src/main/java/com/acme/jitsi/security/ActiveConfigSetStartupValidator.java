package com.acme.jitsi.security;

import com.acme.jitsi.domains.configsets.service.ConfigCompatibilityCheckResult;
import com.acme.jitsi.domains.configsets.service.ConfigCompatibilityMismatch;
import com.acme.jitsi.domains.configsets.service.ConfigCompatibilityMismatchCode;
import com.acme.jitsi.domains.configsets.service.ConfigSet;
import com.acme.jitsi.domains.configsets.service.ConfigSetCompatibilityStateService;
import com.acme.jitsi.domains.configsets.service.ConfigSetDryRunValidator;
import com.acme.jitsi.domains.configsets.service.ConfigSetRepository;
import com.acme.jitsi.domains.configsets.service.ConfigSetStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

@Component
class ActiveConfigSetStartupValidator implements InitializingBean {

  private final ConfigSetRepository configSetRepository;
  private final ConfigSetDryRunValidator configSetDryRunValidator;
  private final ConfigSetCompatibilityStateService compatibilityStateService;

  ActiveConfigSetStartupValidator(
      ConfigSetRepository configSetRepository,
      ConfigSetDryRunValidator configSetDryRunValidator,
      ConfigSetCompatibilityStateService compatibilityStateService) {
    this.configSetRepository = configSetRepository;
    this.configSetDryRunValidator = configSetDryRunValidator;
    this.compatibilityStateService = compatibilityStateService;
  }

  @Override
  public void afterPropertiesSet() {
    UUID traceUuid = UUID.randomUUID();
    String traceId = traceUuid.toString();
    for (ConfigSet activeConfigSet : configSetRepository.findByStatus(ConfigSetStatus.ACTIVE)) {
      ConfigCompatibilityCheckResult compatibilityResult = configSetDryRunValidator
          .validateCompatibility(activeConfigSet, traceId);
      String configSetId = activeConfigSet.configSetId();
      compatibilityStateService.record(configSetId, compatibilityResult);
      boolean compatible = isCompatible(compatibilityResult);
      if (!compatible) {
        String mismatchCodes = formatMismatchCodes(compatibilityResult);
        throw new JwtStartupValidationException(
            JwtStartupValidationErrorCode.CONFIG_INCOMPATIBLE,
            "Active config set is incompatible: "
                + configSetId
                + "; mismatches="
                + mismatchCodes);
      }
    }
  }

  private String formatMismatchCodes(ConfigCompatibilityCheckResult compatibilityResult) {
    List<String> codes = new java.util.ArrayList<>();
    for (ConfigCompatibilityMismatch mismatch : compatibilityResult.mismatches()) {
      String mismatchCode = resolveMismatchCodeName(mismatch.code());
      codes.add(mismatchCode);
    }
    return String.join(",", codes);
  }

  private boolean isCompatible(ConfigCompatibilityCheckResult compatibilityResult) {
    return compatibilityResult.compatible();
  }

  private String resolveMismatchCodeName(ConfigCompatibilityMismatchCode mismatchCode) {
    return switch (mismatchCode) {
      case ISSUER_MISMATCH -> "ISSUER_MISMATCH";
      case AUDIENCE_MISMATCH -> "AUDIENCE_MISMATCH";
      case ROLE_CLAIM_MISMATCH -> "ROLE_CLAIM_MISMATCH";
      case ALGORITHM_KEY_SOURCE_MISMATCH -> "ALGORITHM_KEY_SOURCE_MISMATCH";
      case ENDPOINT_MISMATCH -> "ENDPOINT_MISMATCH";
      case API_VERSION_MISMATCH -> "API_VERSION_MISMATCH";
    };
  }
}