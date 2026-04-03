package com.acme.jitsi.domains.configsets.infrastructure;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.acme.jitsi.domains.configsets.service.ConfigSet;
import com.acme.jitsi.domains.configsets.service.ConfigSetEnvironmentType;
import com.acme.jitsi.domains.configsets.service.ConfigSetRepository;
import com.acme.jitsi.domains.configsets.service.ConfigSetStatus;
import com.acme.jitsi.domains.configsets.usecase.CreateConfigSetCommand;
import com.acme.jitsi.domains.configsets.usecase.CreateConfigSetUseCase;
import com.acme.jitsi.domains.configsets.usecase.RolloutConfigSetCommand;
import com.acme.jitsi.domains.configsets.usecase.RolloutConfigSetUseCase;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;

@ExtendWith(MockitoExtension.class)
class DevActiveConfigSetBootstrapTest {

  @Mock
  private ConfigSetRepository configSetRepository;

  @Mock
  private CreateConfigSetUseCase createConfigSetUseCase;

  @Mock
  private RolloutConfigSetUseCase rolloutConfigSetUseCase;

  private DevActiveConfigSetBootstrap bootstrap;

  @BeforeEach
  void setUp() {
    bootstrap = new DevActiveConfigSetBootstrap(
        configSetRepository,
        createConfigSetUseCase,
        rolloutConfigSetUseCase,
        "tenant-1",
        "Local DEV Config",
        "http://localhost:3000",
        "jitsi-meet",
        "HS256",
        "role",
        "01234567890123456789012345678901",
        20,
        60,
        "http://localhost:8080",
        "v1");
  }

  @Test
  void createsAndRollsOutDraftWhenTenantHasNoConfigSets() throws Exception {
    ConfigSet created = configSet("cfg-created", ConfigSetStatus.DRAFT, Instant.parse("2026-03-20T12:00:00Z"));
    when(configSetRepository.findActiveByTenantIdAndEnvironmentType("tenant-1", ConfigSetEnvironmentType.DEV))
        .thenReturn(Optional.empty());
    when(configSetRepository.findByTenantId("tenant-1", 0, 50)).thenReturn(List.of());
    when(createConfigSetUseCase.execute(argThat(command ->
        command.tenantId().equals("tenant-1")
            && command.environmentType() == ConfigSetEnvironmentType.DEV
            && command.name().equals("Local DEV Config")
            && command.meetingsServiceUrl().equals("http://localhost:8080/api/v1"))))
        .thenReturn(created);

    bootstrap.run(new DefaultApplicationArguments(new String[0]));

    verify(createConfigSetUseCase).execute(argThat(CreateConfigSetCommand.class::isInstance));
    verify(rolloutConfigSetUseCase).execute(argThat(command ->
        command.configSetId().equals("cfg-created")
            && command.tenantId().equals("tenant-1")));
  }

  @Test
  void rollsOutExistingDraftWhenPresent() throws Exception {
    ConfigSet existingDraft = configSet("cfg-draft", ConfigSetStatus.DRAFT, Instant.parse("2026-03-20T11:59:00Z"));
    when(configSetRepository.findActiveByTenantIdAndEnvironmentType("tenant-1", ConfigSetEnvironmentType.DEV))
        .thenReturn(Optional.empty());
    when(configSetRepository.findByTenantId("tenant-1", 0, 50)).thenReturn(List.of(existingDraft));

    bootstrap.run(new DefaultApplicationArguments(new String[0]));

    verify(createConfigSetUseCase, never()).execute(argThat(CreateConfigSetCommand.class::isInstance));
    verify(rolloutConfigSetUseCase).execute(argThat(command ->
        command.configSetId().equals("cfg-draft")
            && command.tenantId().equals("tenant-1")));
  }

  @Test
  void doesNothingWhenActiveConfigSetAlreadyExists() throws Exception {
    when(configSetRepository.findActiveByTenantIdAndEnvironmentType("tenant-1", ConfigSetEnvironmentType.DEV))
        .thenReturn(Optional.of(configSet("cfg-active", ConfigSetStatus.ACTIVE, Instant.parse("2026-03-20T12:00:00Z"))));

    bootstrap.run(new DefaultApplicationArguments(new String[0]));

    verify(configSetRepository, never()).findByTenantId("tenant-1", 0, 50);
    verify(createConfigSetUseCase, never()).execute(argThat(CreateConfigSetCommand.class::isInstance));
    verify(rolloutConfigSetUseCase, never()).execute(argThat(RolloutConfigSetCommand.class::isInstance));
  }

  private ConfigSet configSet(String configSetId, ConfigSetStatus status, Instant updatedAt) {
    return new ConfigSet(
        configSetId,
        "Local DEV Config",
        "tenant-1",
        ConfigSetEnvironmentType.DEV,
        "http://localhost:3000",
        "jitsi-meet",
        "HS256",
        "role",
        "01234567890123456789012345678901",
        null,
        20,
        60,
        "http://localhost:8080/api/v1",
        status,
        Instant.parse("2026-03-20T11:58:00Z"),
        updatedAt);
  }
}