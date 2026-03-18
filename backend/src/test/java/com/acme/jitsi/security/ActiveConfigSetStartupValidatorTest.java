package com.acme.jitsi.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.acme.jitsi.domains.configsets.service.ConfigCompatibilityCheckResult;
import com.acme.jitsi.domains.configsets.service.ConfigCompatibilityMismatch;
import com.acme.jitsi.domains.configsets.service.ConfigCompatibilityMismatchCode;
import com.acme.jitsi.domains.configsets.service.ConfigSet;
import com.acme.jitsi.domains.configsets.service.ConfigSetCompatibilityStateService;
import com.acme.jitsi.domains.configsets.service.ConfigSetDryRunValidator;
import com.acme.jitsi.domains.configsets.service.ConfigSetEnvironmentType;
import com.acme.jitsi.domains.configsets.service.ConfigSetRepository;
import com.acme.jitsi.domains.configsets.service.ConfigSetStatus;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ActiveConfigSetStartupValidatorTest {

  @Mock
  private ConfigSetRepository configSetRepository;

  @Mock
  private ConfigSetDryRunValidator configSetDryRunValidator;

  @Mock
  private ConfigSetCompatibilityStateService compatibilityStateService;

  @Test
  void afterPropertiesSetThrowsWhenActiveConfigSetIsIncompatible() {
    ConfigSet configSet = activeConfigSet();
    ConfigCompatibilityCheckResult result = new ConfigCompatibilityCheckResult(
      false,
      List.of(new ConfigCompatibilityMismatch(
        ConfigCompatibilityMismatchCode.ISSUER_MISMATCH,
        "issuer mismatch",
        "expected",
        "actual")),
      Instant.parse("2026-01-01T00:00:00Z"),
      "trace-startup-1");
    when(configSetRepository.findByStatus(ConfigSetStatus.ACTIVE)).thenReturn(List.of(configSet));
    when(configSetDryRunValidator.validateCompatibility(eq(configSet), anyString()))
      .thenReturn(result);

    ActiveConfigSetStartupValidator validator = new ActiveConfigSetStartupValidator(
        provider(configSetRepository),
        provider(configSetDryRunValidator),
        provider(compatibilityStateService));

    assertThatThrownBy(validator::afterPropertiesSet)
        .isInstanceOf(JwtStartupValidationException.class)
        .extracting(error -> ((JwtStartupValidationException) error).errorCode())
      .isEqualTo(JwtStartupValidationErrorCode.CONFIG_INCOMPATIBLE.name());
    verify(compatibilityStateService).record(configSet.configSetId(), result);
  }

  @Test
  void afterPropertiesSetPassesWhenNoActiveConfigSets() {
    when(configSetRepository.findByStatus(ConfigSetStatus.ACTIVE)).thenReturn(List.of());

    ActiveConfigSetStartupValidator validator = new ActiveConfigSetStartupValidator(
        provider(configSetRepository),
        provider(configSetDryRunValidator),
        provider(compatibilityStateService));

    assertThatCode(validator::afterPropertiesSet).doesNotThrowAnyException();
    verifyNoInteractions(configSetDryRunValidator);
    verifyNoInteractions(compatibilityStateService);
  }

  @Test
  void afterPropertiesSetRecordsCompatibilitySnapshotWhenConfigSetIsCompatible() {
    ConfigSet configSet = activeConfigSet();
    ConfigCompatibilityCheckResult result = new ConfigCompatibilityCheckResult(
        true,
        List.of(),
        Instant.parse("2026-01-01T00:00:00Z"),
        "trace-startup-2");
    when(configSetRepository.findByStatus(ConfigSetStatus.ACTIVE)).thenReturn(List.of(configSet));
    when(configSetDryRunValidator.validateCompatibility(eq(configSet), anyString()))
        .thenReturn(result);

    ActiveConfigSetStartupValidator validator = new ActiveConfigSetStartupValidator(
      provider(configSetRepository),
      provider(configSetDryRunValidator),
      provider(compatibilityStateService));

    assertThatCode(validator::afterPropertiesSet).doesNotThrowAnyException();
    verify(compatibilityStateService).record(configSet.configSetId(), result);
    verify(configSetDryRunValidator).validateCompatibility(eq(configSet), anyString());
    verify(configSetRepository, never()).findByStatus(ConfigSetStatus.DRAFT);
  }

  private ConfigSet activeConfigSet() {
    return new ConfigSet(
        "cs-1",
        "Active",
        "tenant-1",
        ConfigSetEnvironmentType.DEV,
        "https://issuer",
        "aud",
        "HS256",
        "role",
        "secret",
        null,
        20,
        120,
        "https://meet.example.test/v1",
        ConfigSetStatus.ACTIVE,
        Instant.parse("2026-01-01T00:00:00Z"),
        Instant.parse("2026-01-01T00:00:00Z"));
  }

  @SuppressWarnings("unchecked")
  private <T> ObjectProvider<T> provider(T bean) {
    ObjectProvider<T> provider = Mockito.mock(ObjectProvider.class);
    when(provider.getIfAvailable()).thenReturn(bean);
    return provider;
  }
}
