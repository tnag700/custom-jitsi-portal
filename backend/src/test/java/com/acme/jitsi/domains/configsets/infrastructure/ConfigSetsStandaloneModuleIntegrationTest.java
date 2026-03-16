package com.acme.jitsi.domains.configsets.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.acme.jitsi.domains.DomainModuleTestApplication;
import com.acme.jitsi.domains.configsets.event.ConfigSetCreatedEvent;
import com.acme.jitsi.domains.configsets.service.ConfigSetAuditLog;
import com.acme.jitsi.domains.configsets.service.ConfigSetCompatibilityCheckRepository;
import com.acme.jitsi.domains.configsets.service.ConfigSet;
import com.acme.jitsi.domains.configsets.service.ConfigSetEnvironmentType;
import com.acme.jitsi.domains.configsets.service.ConfigSetRepository;
import com.acme.jitsi.domains.configsets.service.ConfigSetRolloutRepository;
import com.acme.jitsi.domains.configsets.service.ConfigSetStatus;
import com.acme.jitsi.domains.configsets.usecase.CreateConfigSetCommand;
import com.acme.jitsi.domains.configsets.usecase.CreateConfigSetUseCase;
import com.acme.jitsi.security.DefaultJwtAlgorithmPolicy;
import com.acme.jitsi.security.JwtAlgorithmPolicy;
import com.acme.jitsi.security.ProblemResponseFacade;
import com.acme.jitsi.security.TenantAccessGuard;
import com.acme.jitsi.shared.observability.FlowObservationFacade;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.modulith.test.ApplicationModuleTest.BootstrapMode;
import org.springframework.modulith.test.PublishedEvents;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@ApplicationModuleTest(
  module = "configsets",
  mode = BootstrapMode.STANDALONE,
  classes = DomainModuleTestApplication.class,
  verifyAutomatically = false)
@SpringBootTest(classes = {
  DomainModuleTestApplication.class,
  ConfigSetsStandaloneModuleIntegrationTest.ModuleTestConfig.class
}, properties = {
    "spring.main.web-application-type=none",
  "APP_CONFIG_SETS_ENCRYPTION_KEY=0123456789ABCDEF0123456789ABCDEF",
    "app.meetings.token.signing-secret=false",
    "app.security.jwt-startup-validation.enabled=false"
})
@Tag("integration")
class ConfigSetsStandaloneModuleIntegrationTest {

  private final CreateConfigSetUseCase createConfigSetUseCase;
  private final ConfigurableApplicationContext applicationContext;

  @MockitoBean
  private ConfigSetRepository configSetRepository;

  @MockitoBean
  private ConfigSetRolloutRepository configSetRolloutRepository;

  @MockitoBean
  private ConfigSetCompatibilityCheckRepository configSetCompatibilityCheckRepository;

  @MockitoBean
  private ConfigSetAuditLog configSetAuditLog;

  @MockitoBean
  private ProblemResponseFacade problemResponseFacade;

  @MockitoBean
  private TenantAccessGuard tenantAccessGuard;

  ConfigSetsStandaloneModuleIntegrationTest(
      CreateConfigSetUseCase createConfigSetUseCase,
      ConfigurableApplicationContext applicationContext) {
    this.createConfigSetUseCase = createConfigSetUseCase;
    this.applicationContext = applicationContext;
  }

  @BeforeEach
  void resetMocks() {
    Mockito.reset(
      configSetRepository,
      configSetRolloutRepository,
      configSetCompatibilityCheckRepository,
      configSetAuditLog,
      problemResponseFacade,
      tenantAccessGuard);
  }

  @Test
  void createsDraftConfigSetAndPublishesCreationEventWithinStandaloneModule(PublishedEvents events) {
    when(configSetRepository.existsByNameAndTenantId("Config A", "tenant-1")).thenReturn(false);
    when(configSetRepository.save(Mockito.any(ConfigSet.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    ConfigSet created = createConfigSetUseCase.execute(new CreateConfigSetCommand(
        "Config A",
        "tenant-1",
        ConfigSetEnvironmentType.DEV,
        "https://issuer.example.test",
        "jitsi-meet",
        "HS256",
        "role",
        "01234567890123456789012345678901",
        null,
        20,
        60,
        "https://meet.example.test",
        "admin-user",
        "trace-configset-module"));

    assertThat(created.status()).isEqualTo(ConfigSetStatus.DRAFT);
    assertThat(created.tenantId()).isEqualTo("tenant-1");
    assertThat(events.ofType(ConfigSetCreatedEvent.class)
      .matching(event -> created.configSetId().equals(event.configSetId())))
        .hasSize(1);
    assertNoBeansFromPackages(
        "com.acme.jitsi.domains.auth",
        "com.acme.jitsi.domains.health",
        "com.acme.jitsi.domains.invites",
        "com.acme.jitsi.domains.meetings",
        "com.acme.jitsi.domains.profiles",
        "com.acme.jitsi.domains.rooms",
        "com.acme.jitsi.domains.store");
  }

  private void assertNoBeansFromPackages(String... packagePrefixes) {
    assertThat(Arrays.stream(applicationContext.getBeanDefinitionNames())
        .map(applicationContext::getType)
        .filter(Objects::nonNull)
        .map(Class::getName)
        .filter(name -> Arrays.stream(packagePrefixes).anyMatch(name::startsWith))
        .toList()).isEmpty();
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class ModuleTestConfig {
    @Bean
    @org.springframework.context.annotation.Primary
    JwtAlgorithmPolicy jwtAlgorithmPolicy() {
      return new DefaultJwtAlgorithmPolicy();
    }

    @Bean
    FlowObservationFacade flowObservationFacade() {
      return FlowObservationFacade.noop();
    }

    @Bean
    MeterRegistry meterRegistry() {
      return new SimpleMeterRegistry();
    }

    @Bean
    Clock clock() {
      return Clock.fixed(Instant.parse("2026-03-13T00:00:00Z"), ZoneOffset.UTC);
    }
  }
}