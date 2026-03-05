package com.acme.jitsi.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FeaturesConfig {

  @Bean
  @ConditionalOnProperty(
      name = "app.features.advanced-monitoring",
      havingValue = "true",
      matchIfMissing = false)
  public FeatureProbe advancedMonitoringProbe() {
    return new FeatureProbe("advanced-monitoring");
  }

  public record FeatureProbe(String featureName) {
  }
}