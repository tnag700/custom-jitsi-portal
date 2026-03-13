package com.acme.jitsi.domains;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication(scanBasePackages = "com.acme.jitsi")
@ConfigurationPropertiesScan("com.acme.jitsi")
public class TestDomainModuleApplication {
}