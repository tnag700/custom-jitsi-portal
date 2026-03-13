package com.acme.jitsi.domains.meetings.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.acme.jitsi.domains.TestDomainModuleApplication;
import com.acme.jitsi.domains.meetings.event.MeetingCreatedEvent;
import com.acme.jitsi.domains.meetings.service.MeetingAuditLog;
import com.acme.jitsi.domains.meetings.service.MeetingInviteRepository;
import com.acme.jitsi.domains.meetings.service.MeetingParticipantAssignmentRepository;
import com.acme.jitsi.domains.meetings.service.MeetingRepository;
import com.acme.jitsi.domains.meetings.usecase.CreateMeetingCommand;
import com.acme.jitsi.domains.meetings.usecase.CreateMeetingUseCase;
import com.acme.jitsi.domains.rooms.service.ConfigSetValidator;
import com.acme.jitsi.domains.rooms.service.Room;
import com.acme.jitsi.domains.rooms.service.RoomRepository;
import com.acme.jitsi.domains.rooms.service.RoomService;
import com.acme.jitsi.domains.rooms.service.RoomStatus;
import com.acme.jitsi.domains.profiles.service.UserProfileRepository;
import com.acme.jitsi.security.DefaultJwtAlgorithmPolicy;
import com.acme.jitsi.security.JwtAlgorithmPolicy;
import com.acme.jitsi.security.ProblemDetailsMappingPolicy;
import com.acme.jitsi.security.ProblemResponseFacade;
import com.acme.jitsi.security.TenantAccessGuard;
import com.acme.jitsi.security.TokenIssuanceCompatibilityPolicy;
import com.acme.jitsi.shared.observability.FlowObservationFacade;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.modulith.test.ApplicationModuleTest.BootstrapMode;
import org.springframework.modulith.test.PublishedEvents;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@ApplicationModuleTest(
  mode = BootstrapMode.DIRECT_DEPENDENCIES,
  classes = TestDomainModuleApplication.class,
  verifyAutomatically = false)
@SpringBootTest(properties = {
  "spring.main.web-application-type=none",
  "spring.main.allow-bean-definition-overriding=true",
    "app.meetings.token.signing-secret=01234567890123456789012345678901",
    "app.meetings.token.issuer=https://portal.example.test",
    "app.meetings.token.audience=jitsi-meet",
    "app.meetings.token.algorithm=HS256",
    "app.meetings.token.ttl-minutes=20",
    "app.meetings.token.role-claim-name=role",
    "app.meetings.token.join-url-template=https://meet.example/%s#jwt=%s"
})
@Import(MeetingsDirectDependenciesModuleIntegrationTest.ModuleTestConfig.class)
@Tag("integration")
class MeetingsDirectDependenciesModuleIntegrationTest {

  private final CreateMeetingUseCase createMeetingUseCase;
  private final ConfigurableApplicationContext applicationContext;

  @MockitoBean
  private MeetingRepository meetingRepository;

  @MockitoBean
  private MeetingInviteRepository meetingInviteRepository;

  @MockitoBean
  private MeetingParticipantAssignmentRepository meetingParticipantAssignmentRepository;

  @MockitoBean
  private MeetingAuditLog meetingAuditLog;

  @MockitoBean
  private RoomRepository roomRepository;

  @MockitoBean
  private UserProfileRepository userProfileRepository;

  @MockitoBean
  private ConfigSetValidator configSetValidator;

  @MockitoBean
  private ProblemResponseFacade problemResponseFacade;

  @MockitoBean
  private TenantAccessGuard tenantAccessGuard;

  @MockitoBean
  private ProblemDetailsMappingPolicy problemDetailsMappingPolicy;

  MeetingsDirectDependenciesModuleIntegrationTest(
      CreateMeetingUseCase createMeetingUseCase,
      ConfigurableApplicationContext applicationContext) {
    this.createMeetingUseCase = createMeetingUseCase;
    this.applicationContext = applicationContext;
  }

  @BeforeEach
  void resetMocks() {
    Mockito.reset(
      meetingRepository,
      meetingInviteRepository,
        meetingParticipantAssignmentRepository,
      meetingAuditLog,
      roomRepository,
      userProfileRepository,
      configSetValidator,
      problemResponseFacade,
      tenantAccessGuard,
      problemDetailsMappingPolicy);
    when(configSetValidator.isValid(Mockito.anyString())).thenReturn(true);
    when(meetingParticipantAssignmentRepository.findByMeetingId(Mockito.anyString())).thenReturn(java.util.List.of());
    when(meetingParticipantAssignmentRepository.findBySubjectId(Mockito.anyString())).thenReturn(java.util.List.of());
    when(meetingParticipantAssignmentRepository.findByMeetingIdAndSubjectId(Mockito.anyString(), Mockito.anyString()))
      .thenReturn(Optional.empty());
  }

  @Test
  void createsMeetingThroughAllowedRoomsCollaboratorPathOnly(PublishedEvents events) {
    when(roomRepository.findById("room-1")).thenReturn(Optional.of(new Room(
        "room-1",
        "Planning Room",
        null,
        "tenant-1",
        "config-1",
        RoomStatus.ACTIVE,
        Instant.parse("2026-03-10T10:00:00Z"),
        Instant.parse("2026-03-10T10:00:00Z"))));
      when(meetingRepository.save(Mockito.any(com.acme.jitsi.domains.meetings.service.Meeting.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    var created = createMeetingUseCase.execute(new CreateMeetingCommand(
        "room-1",
        "Architecture Review",
        "Bounded module bootstrap",
        "scheduled",
        Instant.parse("2026-03-20T10:00:00Z"),
        Instant.parse("2026-03-20T11:00:00Z"),
        true,
        false,
        "admin-user",
        "trace-meeting-module"));

    assertThat(created.roomId()).isEqualTo("room-1");
    assertThat(applicationContext.getBeansOfType(RoomService.class)).isNotEmpty();
    assertThat(events.ofType(MeetingCreatedEvent.class)
      .matching(event -> created.meetingId().equals(event.meetingId())))
        .hasSize(1);
    assertNoBeansFromPackages(
        "com.acme.jitsi.domains.auth",
        "com.acme.jitsi.domains.health",
        "com.acme.jitsi.domains.invites",
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

  @TestConfiguration
  static class ModuleTestConfig {
    @Bean
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
    MeterRegistry meterRegistry() {
      return new SimpleMeterRegistry();
    }

    @Bean
    Clock clock() {
      return Clock.fixed(Instant.parse("2026-03-13T00:00:00Z"), ZoneOffset.UTC);
    }
  }
}