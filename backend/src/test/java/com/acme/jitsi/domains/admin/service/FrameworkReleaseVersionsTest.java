package com.acme.jitsi.domains.admin.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FrameworkReleaseVersionsTest {

  @Test
  void comparesStablePatchVersionsNumerically() {
    assertThat(FrameworkReleaseVersions.compare("4.1.11", "4.1.9")).isEqualTo(1);
    assertThat(FrameworkReleaseVersions.compare("4.1.0", "4.1.0")).isZero();
    assertThat(FrameworkReleaseVersions.compare("4.0.9", "4.1.0")).isEqualTo(-1);
  }

  @Test
  void comparesBetaChannelAndStableRelease() {
    assertThat(FrameworkReleaseVersions.compare("2.0.0-beta.40", "2.0.0-beta.38"))
        .isEqualTo(1);
    assertThat(FrameworkReleaseVersions.compare("2.0.0", "2.0.0-beta.40"))
        .isEqualTo(1);
  }

  @Test
  void rejectsUnparseableProviderVersionsInsteadOfClaimingUpToDate() {
    assertThat(FrameworkReleaseVersions.compare("unknown", "4.1.0")).isNull();
    assertThat(FrameworkReleaseVersions.compare("4.1.1-SNAPSHOT", "4.1.0")).isNull();
  }
}
