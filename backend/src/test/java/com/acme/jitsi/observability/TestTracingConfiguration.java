package com.acme.jitsi.observability;

import io.opentelemetry.sdk.trace.export.SpanExporter;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@TestConfiguration(proxyBeanMethods = false)
class TestTracingConfiguration {

  @Bean
  TestSpanExporter testSpanExporter() {
    return new TestSpanExporter();
  }

  @Bean
  SpanExporter spanExporter(TestSpanExporter testSpanExporter) {
    return testSpanExporter;
  }
}