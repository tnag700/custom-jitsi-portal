package com.acme.jitsi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.AutoConfigurationExcludeFilter;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

@SpringBootApplication
@ComponentScan(
    basePackages = "com.acme.jitsi",
    excludeFilters = {
  @ComponentScan.Filter(type = FilterType.CUSTOM, classes = TypeExcludeFilter.class),
  @ComponentScan.Filter(type = FilterType.CUSTOM, classes = AutoConfigurationExcludeFilter.class),
      @ComponentScan.Filter(
          type = FilterType.REGEX,
          pattern = "com\\.acme\\.jitsi\\.(domains\\.DomainModuleTestApplication|support\\.TestDomainModuleApplication)")
    })
@ConfigurationPropertiesScan
public class Application {

  public static void main(String[] args) {
    SpringApplication.run(Application.class, args);
  }
}
