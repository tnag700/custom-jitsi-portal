package com.acme.jitsi.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.acme.jitsi.domains.DomainModuleTopology;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModule;
import org.springframework.modulith.core.ApplicationModules;

class BackendModulithVerificationTest {

  private static final Set<String> EXPECTED_MODULES = Set.of(
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
        .map(ApplicationModule::getName)
        .collect(Collectors.toCollection(TreeSet::new));

    assertThat(moduleNames)
        .containsExactlyInAnyOrderElementsOf(EXPECTED_MODULES)
        .doesNotContain("config", "domains", "infrastructure", "integrations", "security", "shared");
  }

  @Test
  void verifiesBusinessDomainBoundaries() {
    ApplicationModules.of(DomainModuleTopology.class).verify();
  }
}