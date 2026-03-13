package com.acme.jitsi.domains.meetings.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.acme.jitsi.shared.pipeline.OrderedPipelineConfigurationException;
import com.acme.jitsi.shared.ErrorCode;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.http.HttpStatus;

class MeetingRoleResolverTest {

  private static final List<MeetingRoleResolutionPolicy> DEFAULT_POLICIES = List.of(
      new BlockedSubjectMeetingRoleResolutionPolicy(),
      unknownMeetingPolicy(),
      dbAssignmentPolicy(),
      new ExplicitAssignmentMeetingRoleResolutionPolicy(),
      new UnknownRolePolicyMeetingRoleResolutionPolicy());

  @Test
  void defaultPolicyRejectsJoinWhenAssignmentMissing() {
    MeetingTokenProperties properties = new MeetingTokenProperties();
    properties.setKnownMeetingIds(List.of("meeting-a"));

    MeetingRoleResolver resolver = new MeetingRoleResolver(properties, DEFAULT_POLICIES);

    assertThatThrownBy(() -> resolver.resolve("meeting-a", "u-participant"))
        .isInstanceOf(MeetingTokenException.class)
        .satisfies(ex -> {
          MeetingTokenException error = (MeetingTokenException) ex;
          assertThat(error.status()).isEqualTo(HttpStatus.FORBIDDEN);
          assertThat(error.errorCode()).isEqualTo(ErrorCode.ACCESS_DENIED.code());
        });
  }

  @Test
  void fallbackPolicyReturnsParticipantWhenAssignmentMissing() {
    MeetingTokenProperties properties = new MeetingTokenProperties();
    properties.setUnknownRolePolicy("fallback-participant");
    properties.setKnownMeetingIds(List.of("meeting-a"));

    MeetingRoleResolver resolver = new MeetingRoleResolver(properties, DEFAULT_POLICIES);

    MeetingRole resolvedRole = resolver.resolve("meeting-a", "u-participant");

    assertThat(resolvedRole).isEqualTo(MeetingRole.PARTICIPANT);
  }

  @Test
  void denyPolicyRejectsJoinWhenAssignmentMissing() {
    MeetingTokenProperties properties = new MeetingTokenProperties();
    properties.setUnknownRolePolicy("deny-access");
    properties.setKnownMeetingIds(List.of("meeting-a"));

    MeetingRoleResolver resolver = new MeetingRoleResolver(properties, DEFAULT_POLICIES);

    assertThatThrownBy(() -> resolver.resolve("meeting-a", "u-participant"))
        .isInstanceOf(MeetingTokenException.class)
        .satisfies(ex -> {
          MeetingTokenException error = (MeetingTokenException) ex;
          assertThat(error.status()).isEqualTo(HttpStatus.FORBIDDEN);
          assertThat(error.errorCode()).isEqualTo(ErrorCode.ACCESS_DENIED.code());
        });
  }

  @Test
  void unsupportedConfiguredRoleReturnsRoleMismatch() {
    MeetingTokenProperties properties = new MeetingTokenProperties();
    properties.setUnknownRolePolicy("fallback-participant");
    properties.setKnownMeetingIds(List.of("meeting-a"));

    MeetingTokenProperties.RoleAssignment assignment = new MeetingTokenProperties.RoleAssignment();
    assignment.setMeetingId("meeting-a");
    assignment.setSubject("u-host");
    assignment.setRole("admin");
    properties.setAssignments(List.of(assignment));

    MeetingRoleResolver resolver = new MeetingRoleResolver(properties, DEFAULT_POLICIES);

    assertThatThrownBy(() -> resolver.resolve("meeting-a", "u-host"))
        .isInstanceOf(MeetingTokenException.class)
        .satisfies(ex -> {
          MeetingTokenException error = (MeetingTokenException) ex;
          assertThat(error.status()).isEqualTo(HttpStatus.CONFLICT);
          assertThat(error.errorCode()).isEqualTo(ErrorCode.ROLE_MISMATCH.code());
        });
  }

  @Test
  void blockedSubjectReturnsAccessDenied() {
    MeetingTokenProperties properties = new MeetingTokenProperties();
    properties.setKnownMeetingIds(List.of("meeting-a"));
    properties.setBlockedSubjects(Set.of("u-blocked"));

    MeetingRoleResolver resolver = new MeetingRoleResolver(properties, DEFAULT_POLICIES);

    assertThatThrownBy(() -> resolver.resolve("meeting-a", "u-blocked"))
        .isInstanceOf(MeetingTokenException.class)
        .satisfies(ex -> {
          MeetingTokenException error = (MeetingTokenException) ex;
          assertThat(error.status()).isEqualTo(HttpStatus.FORBIDDEN);
          assertThat(error.errorCode()).isEqualTo(ErrorCode.ACCESS_DENIED.code());
        });
  }

  @Test
  void unknownMeetingReturnsMeetingNotFound() {
    MeetingTokenProperties properties = new MeetingTokenProperties();
    properties.setKnownMeetingIds(List.of("meeting-a"));

    MeetingRoleResolver resolver = new MeetingRoleResolver(properties, DEFAULT_POLICIES);

    assertThatThrownBy(() -> resolver.resolve("meeting-missing", "u-participant"))
        .isInstanceOf(MeetingTokenException.class)
        .satisfies(ex -> {
          MeetingTokenException error = (MeetingTokenException) ex;
          assertThat(error.status()).isEqualTo(HttpStatus.NOT_FOUND);
          assertThat(error.errorCode()).isEqualTo(ErrorCode.MEETING_NOT_FOUND.code());
        });
  }

  @Test
  void ambiguousAssignmentsReturnRoleMismatch() {
    MeetingTokenProperties properties = new MeetingTokenProperties();
    properties.setUnknownRolePolicy("fallback-participant");
    properties.setKnownMeetingIds(List.of("meeting-a"));

    MeetingTokenProperties.RoleAssignment host = new MeetingTokenProperties.RoleAssignment();
    host.setMeetingId("meeting-a");
    host.setSubject("u-conflict");
    host.setRole("host");

    MeetingTokenProperties.RoleAssignment moderator = new MeetingTokenProperties.RoleAssignment();
    moderator.setMeetingId("meeting-a");
    moderator.setSubject("u-conflict");
    moderator.setRole("moderator");

    properties.setAssignments(List.of(host, moderator));

    MeetingRoleResolver resolver = new MeetingRoleResolver(properties, DEFAULT_POLICIES);

    assertThatThrownBy(() -> resolver.resolve("meeting-a", "u-conflict"))
        .isInstanceOf(MeetingTokenException.class)
        .satisfies(ex -> {
          MeetingTokenException error = (MeetingTokenException) ex;
          assertThat(error.status()).isEqualTo(HttpStatus.CONFLICT);
          assertThat(error.errorCode()).isEqualTo(ErrorCode.ROLE_MISMATCH.code());
        });
  }

  @Test
  void executesPoliciesInProvidedOrderUntilDecision() {
    MeetingTokenProperties properties = new MeetingTokenProperties();
    properties.setKnownMeetingIds(List.of("meeting-a"));
    properties.setBlockedSubjects(Set.of("u-blocked"));

    MeetingRoleResolver resolver = new MeetingRoleResolver(properties, List.of(
        new UnknownRolePolicyMeetingRoleResolutionPolicy(),
        new ExplicitAssignmentMeetingRoleResolutionPolicy(),
        dbAssignmentPolicy(),
        unknownMeetingPolicy(),
        new BlockedSubjectMeetingRoleResolutionPolicy()));

    assertThatThrownBy(() -> resolver.resolve("meeting-missing", "u-blocked"))
        .isInstanceOf(MeetingTokenException.class)
        .satisfies(ex -> {
          MeetingTokenException error = (MeetingTokenException) ex;
          assertThat(error.status()).isEqualTo(HttpStatus.FORBIDDEN);
          assertThat(error.errorCode()).isEqualTo(ErrorCode.ACCESS_DENIED.code());
        });
  }

  @Test
  void productionPoliciesHaveStableExecutionOrder() {
    List<MeetingRoleResolutionPolicy> policies = new java.util.ArrayList<>(List.of(
        new UnknownRolePolicyMeetingRoleResolutionPolicy(),
        new ExplicitAssignmentMeetingRoleResolutionPolicy(),
        dbAssignmentPolicy(),
        unknownMeetingPolicy(),
        new BlockedSubjectMeetingRoleResolutionPolicy()));

    AnnotationAwareOrderComparator.sort(policies);

    assertThat(policies)
        .extracting(policy -> policy.getClass().getSimpleName())
        .containsExactly(
            "BlockedSubjectMeetingRoleResolutionPolicy",
            "UnknownMeetingMeetingRoleResolutionPolicy",
            "DbParticipantAssignmentMeetingRoleResolutionPolicy",
            "ExplicitAssignmentMeetingRoleResolutionPolicy",
            "UnknownRolePolicyMeetingRoleResolutionPolicy");
  }

  @Test
  void failsFastWhenPipelineHasNoTerminalPolicy() {
    MeetingTokenProperties properties = new MeetingTokenProperties();
    properties.setKnownMeetingIds(List.of("meeting-a"));

    assertThatThrownBy(() -> new MeetingRoleResolver(properties, List.of(
        new BlockedSubjectMeetingRoleResolutionPolicy(),
        unknownMeetingPolicy(),
        dbAssignmentPolicy(),
        new ExplicitAssignmentMeetingRoleResolutionPolicy())))
      .isInstanceOf(OrderedPipelineConfigurationException.class)
        .hasMessageContaining("terminal")
        .hasMessageContaining("MeetingRoleResolver");
  }

  @Test
  void failsFastWhenTerminalPolicyIsNotOrderedLast() {
    MeetingTokenProperties properties = new MeetingTokenProperties();
    properties.setKnownMeetingIds(List.of("meeting-a"));

    assertThatThrownBy(() -> new MeetingRoleResolver(properties, List.of(
        new BlockedSubjectMeetingRoleResolutionPolicy(),
        unknownMeetingPolicy(),
      dbAssignmentPolicy(),
        new ExplicitAssignmentMeetingRoleResolutionPolicy(),
        new UnknownRolePolicyMeetingRoleResolutionPolicy(),
        new LatePassThroughPolicy())))
      .isInstanceOf(OrderedPipelineConfigurationException.class)
      .hasMessageContaining("terminal step must be ordered last");
  }

  private static MeetingRoleResolutionPolicy unknownMeetingPolicy() {
    MeetingRepository meetingRepository = mock(MeetingRepository.class);
    when(meetingRepository.existsById(anyString())).thenReturn(false);
    return new UnknownMeetingMeetingRoleResolutionPolicy(meetingRepository);
  }

  private static MeetingRoleResolutionPolicy dbAssignmentPolicy() {
    MeetingParticipantAssignmentRepository assignmentRepository = mock(MeetingParticipantAssignmentRepository.class);
    when(assignmentRepository.findByMeetingIdAndSubjectId(anyString(), anyString())).thenReturn(Optional.empty());
    return new DbParticipantAssignmentMeetingRoleResolutionPolicy(assignmentRepository);
  }

  @org.springframework.core.annotation.Order(500)
  private static final class LatePassThroughPolicy implements MeetingRoleResolutionPolicy {

    @Override
    public java.util.Optional<MeetingRole> resolve(MeetingRoleResolutionContext context) {
      return java.util.Optional.empty();
    }
  }
}
