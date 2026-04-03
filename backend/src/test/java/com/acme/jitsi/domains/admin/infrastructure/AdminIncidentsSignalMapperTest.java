package com.acme.jitsi.domains.admin.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.acme.jitsi.domains.admin.service.AdminIncidentsReadModel;
import com.acme.jitsi.domains.configsets.service.ConfigSetEnvironmentType;
import java.sql.Timestamp;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class AdminIncidentsSignalMapperTest {

  private final AdminIncidentsSignalMapper mapper = new AdminIncidentsSignalMapper();

  @Test
  void mapsMeetingSignalFromChangedFields() {
    AdminIncidentsReadModel.IncidentSignal signal = mapper.toMeetingSignal(new Object[] {
        Timestamp.from(Instant.parse("2026-03-18T09:55:00Z")),
        "tenant-1",
        "DEV",
        "room-1",
        "meeting-1",
        "subject-1",
        "trace-1",
        "errorCode=TOKEN_INVALID,reasonCategory=TOKEN,role=moderator"
    });

    assertThat(signal.environmentType()).isEqualTo(ConfigSetEnvironmentType.DEV);
    assertThat(signal.errorCode()).isEqualTo("TOKEN_INVALID");
    assertThat(signal.category()).isEqualTo("TOKEN");
    assertThat(signal.role()).isEqualTo("moderator");
    assertThat(signal.diagnosticResult())
        .isEqualTo("Не удалось завершить вход. Повторите SSO-authentication и проверьте токен.");
    assertThat(signal.traceId()).isEqualTo("trace-1");
    assertThat(signal.requestId()).isEqualTo("trace-1");
  }

  @Test
  void mapsAuthSignalCategoryAndFallbackDiagnostic() {
    AdminIncidentsReadModel.IncidentSignal signal = mapper.toAuthSignal(new Object[] {
        Timestamp.from(Instant.parse("2026-03-18T09:56:00Z")),
        "tenant-1",
        "TEST",
        "room-1",
        "meeting-1",
        "subject-1",
        "trace-2",
        "AUTH_REQUIRED",
        null,
        "SSO_LOGIN_FAILED"
    });

    assertThat(signal.environmentType()).isEqualTo(ConfigSetEnvironmentType.TEST);
    assertThat(signal.category()).isEqualTo("SSO");
    assertThat(signal.diagnosticResult())
        .isEqualTo("Не удалось завершить вход. Повторите SSO-authentication и проверьте токен.");
  }

  @Test
  void mapsConfigSignalDefaults() {
    AdminIncidentsReadModel.IncidentSignal signal = mapper.toConfigSignal(new Object[] {
        Timestamp.from(Instant.parse("2026-03-18T09:57:00Z")),
        "tenant-1",
        "PROD",
        "trace-3",
        null
    });

    assertThat(signal.environmentType()).isEqualTo(ConfigSetEnvironmentType.PROD);
    assertThat(signal.errorCode()).isEqualTo("CONFIG_INCOMPATIBLE");
    assertThat(signal.category()).isEqualTo("CONFIG");
    assertThat(signal.alertSeverity()).isEqualTo("critical");
    assertThat(signal.joinReadinessStatus()).isEqualTo("blocked");
    assertThat(signal.diagnosticResult())
        .isEqualTo("Активный конфиг-контур несовместим с требованиями входа.");
  }
}