package com.acme.jitsi.domains.invites.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.acme.jitsi.domains.DomainModuleTestApplication;
import com.acme.jitsi.domains.invites.service.InviteMeetingStatePort;
import com.acme.jitsi.domains.invites.service.InviteReservation;
import com.acme.jitsi.domains.invites.service.InviteValidationPort;
import com.acme.jitsi.domains.meetings.service.MeetingInvite;
import com.acme.jitsi.domains.meetings.service.MeetingInviteService;
import com.acme.jitsi.domains.meetings.service.MeetingRole;
import com.acme.jitsi.domains.meetings.usecase.ConsumeInviteCommand;
import com.acme.jitsi.domains.meetings.usecase.ConsumeInviteUseCase;
import com.acme.jitsi.security.DefaultJwtAlgorithmPolicy;
import com.acme.jitsi.security.JwtAlgorithmPolicy;
import com.acme.jitsi.security.ProblemDetailsMappingPolicy;
import com.acme.jitsi.security.ProblemResponseFacade;
import com.acme.jitsi.security.TenantAccessGuard;
import com.acme.jitsi.security.TokenIssuanceCompatibilityPolicy;
import com.acme.jitsi.support.MeetingsModuleScaffoldingMocksSupport;
import com.acme.jitsi.shared.observability.FlowObservationFacade;
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
import org.springframework.context.annotation.Primary;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.modulith.test.ApplicationModuleTest.BootstrapMode;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@ApplicationModuleTest(
  module = "invites",
  mode = BootstrapMode.DIRECT_DEPENDENCIES,
  classes = DomainModuleTestApplication.class,
  verifyAutomatically = false)
@SpringBootTest(classes = {
  DomainModuleTestApplication.class,
  InvitesDirectDependenciesModuleIntegrationTest.ModuleTestConfig.class
}, properties = {
    "spring.main.web-application-type=none",
    "app.invites.mode=database",
    "app.meetings.token.signing-secret=01234567890123456789012345678901",
    "app.meetings.token.issuer=https://portal.example.test",
    "app.meetings.token.audience=jitsi-meet",
    "app.meetings.token.algorithm=HS256",
    "app.meetings.token.ttl-minutes=20",
    "app.meetings.token.role-claim-name=role",
    "app.meetings.token.join-url-template=https://meet.example/%s#jwt=%s",
    "app.security.jwt-startup-validation.enabled=false"
})
@Tag("integration")
class InvitesDirectDependenciesModuleIntegrationTest extends MeetingsModuleScaffoldingMocksSupport {

  private final InviteValidationPort inviteValidationPort;
  private final ConfigurableApplicationContext applicationContext;

  @MockitoBean
  private MeetingInviteService meetingInviteService;

  @MockitoBean
  private ConsumeInviteUseCase consumeInviteUseCase;

  @MockitoBean
  private InviteMeetingStatePort inviteMeetingStatePort;

  @MockitoBean
  private ProblemDetailsMappingPolicy problemDetailsMappingPolicy;

  @MockitoBean
  private ProblemResponseFacade problemResponseFacade;

  @MockitoBean
  private TenantAccessGuard tenantAccessGuard;

  InvitesDirectDependenciesModuleIntegrationTest(
      InviteValidationPort inviteValidationPort,
      ConfigurableApplicationContext applicationContext) {
    this.inviteValidationPort = inviteValidationPort;
    this.applicationContext = applicationContext;
  }

  @BeforeEach
  void resetMocks() {
    Mockito.reset(
        meetingInviteService,
        consumeInviteUseCase,
        inviteMeetingStatePort,
        userProfileService,
        roomService,
        configSetValidator,
        problemDetailsMappingPolicy,
        problemResponseFacade,
        tenantAccessGuard);
  }

  @Test
  void validatesInviteViaAllowedMeetingsServiceWithoutUnrelatedModules() {
    when(meetingInviteService.findByToken("invite-token")).thenReturn(java.util.Optional.of(new MeetingInvite(
        "invite-1",
        "meeting-a",
        "invite-token",
        MeetingRole.PARTICIPANT,
        3,
        0,
        Instant.parse("2026-03-30T00:00:00Z"),
        null,
        Instant.parse("2026-03-13T00:00:00Z"),
        "admin-user")));

    var validation = inviteValidationPort.validate("invite-token");

    assertThat(validation.meetingId()).isEqualTo("meeting-a");
    verify(meetingInviteService).findByToken("invite-token");
    verify(inviteMeetingStatePort).assertJoinAllowed("meeting-a");
    assertNoBeansFromPackages(
        "com.acme.jitsi.domains.auth",
      "com.acme.jitsi.domains.health");
  }

  @Test
  void reservesInviteViaAllowedMeetingsUseCaseWithoutUnrelatedModules() {
    when(consumeInviteUseCase.execute(Mockito.any(ConsumeInviteCommand.class))).thenReturn(new MeetingInvite(
        "invite-1",
        "meeting-a",
        "invite-token",
        MeetingRole.PARTICIPANT,
        3,
        1,
        Instant.parse("2026-03-30T00:00:00Z"),
        null,
        Instant.parse("2026-03-13T00:00:00Z"),
        "admin-user"));

    InviteReservation reservation = inviteValidationPort.reserve("invite-token");

    assertThat(reservation.meetingId()).isEqualTo("meeting-a");
    verify(consumeInviteUseCase).execute(Mockito.any(ConsumeInviteCommand.class));
    assertNoBeansFromPackages(
        "com.acme.jitsi.domains.auth",
      "com.acme.jitsi.domains.health");
  }

  private void assertNoBeansFromPackages(String... packagePrefixes) {
    assertThat(Arrays.stream(applicationContext.getBeanDefinitionNames())
        .map(applicationContext::getType)
        .filter(Objects::nonNull)
        .map(Class::getName)
        .filter(name -> Arrays.stream(packagePrefixes).anyMatch(name::startsWith))
        .toList()).isEmpty();
  }

  @TestConfiguration
  static class ModuleTestConfig {
    @Bean
    @Primary
    JwtAlgorithmPolicy jwtAlgorithmPolicy() {
      return new DefaultJwtAlgorithmPolicy();
    }

    @Bean
    TokenIssuanceCompatibilityPolicy tokenIssuanceCompatibilityPolicy() {
      return Mockito.mock(TokenIssuanceCompatibilityPolicy.class);
    }

    @Bean
    FlowObservationFacade flowObservationFacade() {
      return FlowObservationFacade.noop();
    }

    @Bean
    Clock clock() {
      return Clock.fixed(Instant.parse("2026-03-13T00:00:00Z"), ZoneOffset.UTC);
    }
  }
}