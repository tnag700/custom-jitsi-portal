package com.acme.jitsi.domains.configsets.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.acme.jitsi.domains.configsets.service.ConfigSet;
import com.acme.jitsi.domains.configsets.service.ConfigSetEnvironmentType;
import com.acme.jitsi.domains.configsets.service.ConfigSetRepository;
import com.acme.jitsi.domains.configsets.service.ConfigSetStatus;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DatabaseConfigSetValidatorTest {

  @Mock
  private ConfigSetRepository repository;

  private DatabaseConfigSetValidator validator;

  @BeforeEach
  void setUp() {
    validator = new DatabaseConfigSetValidator(repository);
  }

  @Test
  void returnsTrueWhenConfigSetExistsAndIsActive() {
    when(repository.findById("cs-1")).thenReturn(Optional.of(base(ConfigSetStatus.ACTIVE)));
    assertThat(validator.isValid("cs-1")).isTrue();
  }

  @Test
  void returnsFalseWhenConfigSetNotFound() {
    when(repository.findById("missing")).thenReturn(Optional.empty());
    assertThat(validator.isValid("missing")).isFalse();
  }

  private ConfigSet base(ConfigSetStatus status) {
    return new ConfigSet(
        "cs-1",
        "Config",
        "tenant-1",
        ConfigSetEnvironmentType.DEV,
        "issuer",
        "audience",
        "HS256",
        "role",
        "secret",
        null,
        20,
        120,
        "https://meet.example.test",
        status,
        Instant.parse("2026-01-01T00:00:00Z"),
        Instant.parse("2026-01-01T00:00:00Z"));
  }
}