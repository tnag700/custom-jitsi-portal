package com.acme.jitsi.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.acme.jitsi.domains.DomainModuleTopology;
import com.acme.jitsi.domains.configsets.service.ConfigSetRepository;
import com.acme.jitsi.domains.configsets.service.ConfigSetCompatibilityCheckRepository;
import com.acme.jitsi.domains.configsets.service.ConfigSetRolloutRepository;
import com.acme.jitsi.domains.meetings.usecase.ConsumeInviteAttemptExecutor;
import com.acme.jitsi.domains.meetings.usecase.ConsumeInviteConcurrencyBoundary;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class BackendModulithVerificationTest {

  private static final Set<String> EXPECTED_MODULES = Set.of(
      "admin",
      "auth",
      "configsets",
      "health",
      "invites",
      "meetings",
      "profiles",
      "rooms",
      "store");

  @Test
  void modelsOnlyBusinessDomainModules() {
    TreeSet<String> moduleNames = ApplicationModules.of(DomainModuleTopology.class)
        .stream()
        .map(module -> module.getIdentifier().toString())
        .collect(Collectors.toCollection(TreeSet::new));

    assertThat(moduleNames)
        .containsExactlyInAnyOrderElementsOf(EXPECTED_MODULES)
        .doesNotContain("config", "domains", "infrastructure", "integrations", "security", "shared");
  }

  @Test
  void repositoriesAndTransactionMachineryAreInternal() {
    ApplicationModules modules = ApplicationModules.of(DomainModuleTopology.class);
    var configsets = modules.getModuleByName("configsets").orElseThrow();
    assertThat(configsets.isExposed(ConfigSetRepository.class)).isFalse();
    assertThat(configsets.isExposed(ConfigSetCompatibilityCheckRepository.class)).isFalse();
    assertThat(configsets.isExposed(ConfigSetRolloutRepository.class)).isFalse();
    var meetings = modules.getModuleByName("meetings").orElseThrow();
    assertThat(meetings.isExposed(ConsumeInviteAttemptExecutor.class)).isFalse();
    assertThat(meetings.isExposed(ConsumeInviteConcurrencyBoundary.class)).isFalse();
  }

  @Test
  void verifiesBusinessDomainBoundaries() {
    ApplicationModules.of(DomainModuleTopology.class).verify();
  }
}
