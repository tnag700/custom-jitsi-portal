package com.acme.jitsi.support;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.autoconfigure.AutoConfigurationExcludeFilter;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import com.acme.jitsi.Application;
import com.acme.jitsi.domains.DomainModuleTestApplication;

@SpringBootConfiguration(proxyBeanMethods = false)
@EnableAutoConfiguration
@AutoConfigurationPackage(basePackages = "com.acme.jitsi")
@TestComponent
@ComponentScan(
		basePackages = "com.acme.jitsi",
		excludeFilters = {
			@ComponentScan.Filter(type = FilterType.CUSTOM, classes = TypeExcludeFilter.class),
			@ComponentScan.Filter(type = FilterType.CUSTOM, classes = AutoConfigurationExcludeFilter.class),
			@ComponentScan.Filter(type = FilterType.ANNOTATION, classes = TestComponent.class),
			@ComponentScan.Filter(type = FilterType.REGEX, pattern = "com\\.acme\\.jitsi\\..*Test.*"),
			@ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = Application.class),
			@ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = DomainModuleTestApplication.class),
			@ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = TestDomainModuleApplication.class)
		})
@ConfigurationPropertiesScan("com.acme.jitsi")
public class TestDomainModuleApplication {
}