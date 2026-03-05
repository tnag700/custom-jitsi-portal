package com.acme.jitsi.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;

class FeaturesConfigTest {

  @SpringBootConfiguration
  @EnableAutoConfiguration
  @Import(FeaturesConfig.class)
  static class TestApplication {
  }

  @Nested
  @SpringBootTest(classes = TestApplication.class, properties = "app.features.advanced-monitoring=false")
  class DisabledFeature {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void shouldNotCreateBeanWhenFeatureDisabled() {
      assertFalse(applicationContext.containsBean("advancedMonitoringProbe"));
    }
  }

  @Nested
  @SpringBootTest(classes = TestApplication.class, properties = "app.features.advanced-monitoring=true")
  class EnabledFeature {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void shouldCreateBeanWhenFeatureEnabled() {
      assertTrue(applicationContext.containsBean("advancedMonitoringProbe"));
      FeaturesConfig.FeatureProbe probe = applicationContext.getBean(FeaturesConfig.FeatureProbe.class);
      assertNotNull(probe);
      assertEquals("advanced-monitoring", probe.featureName());
    }
  }

  @Nested
  @SpringBootTest(classes = TestApplication.class)
  class DefaultFeatureConfiguration {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void shouldNotCreateBeanWhenFeatureDefaultsToFalseFromConfiguration() {
      assertFalse(applicationContext.containsBean("advancedMonitoringProbe"));
    }
  }
}