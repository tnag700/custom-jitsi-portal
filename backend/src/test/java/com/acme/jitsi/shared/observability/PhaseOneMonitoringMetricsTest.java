package com.acme.jitsi.shared.observability;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import org.junit.jupiter.api.Test;

class PhaseOneMonitoringMetricsTest {

  @Test
  void normalizeEventTypeUsesRootLocale() {
    Locale previous = Locale.getDefault();
    Locale.setDefault(Locale.forLanguageTag("tr-TR"));

    try {
      assertThat(PhaseOneMonitoringMetrics.normalizeEventType("TOKEN_ISSUED"))
          .isEqualTo("token_issued");
    } finally {
      Locale.setDefault(previous);
    }
  }
}