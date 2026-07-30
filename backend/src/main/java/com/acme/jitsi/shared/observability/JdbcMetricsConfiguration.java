package com.acme.jitsi.shared.observability;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.config.MeterFilterReply;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Keeps JDBC metrics compatible with Prometheus' stable label-set requirement.
 *
 * <p>Datasource Micrometer can produce different optional SQL tag sets for
 * statements under one metric name. JDBC traces and HikariCP pool metrics are
 * kept, while the incompatible generic JDBC metric family is suppressed.
 */
@Configuration(proxyBeanMethods = false)
class JdbcMetricsConfiguration {

  @Bean
  MeterFilter denyUnstableJdbcMeters() {
    return new MeterFilter() {
      @Override
      public MeterFilterReply accept(Meter.Id id) {
        String name = id.getName();
        if (name.startsWith("jdbc.")) {
          return MeterFilterReply.DENY;
        }
        return MeterFilterReply.NEUTRAL;
      }
    };
  }
}
