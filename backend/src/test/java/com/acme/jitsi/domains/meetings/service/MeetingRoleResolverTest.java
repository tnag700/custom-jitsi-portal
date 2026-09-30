package com.acme.jitsi.domains.meetings.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.acme.jitsi.shared.ErrorCode;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class MeetingRoleResolverTest {

  @Test
  void blankUnknownRolePolicyRemainsDenyByDefault() {
    MeetingTokenProperties properties = new MeetingTokenProperties();

    properties.setUnknownRolePolicy(" ");

    assertThat(properties.unknownRolePolicy())
        .isEqualTo(MeetingTokenProperties.UnknownRolePolicy.DENY_ACCESS);
  }

  @Test
  void defaultPolicyRejectsJoinWhenAssignmentMissing() {
    MeetingTokenProperties properties = new MeetingTokenProperties();
    properties.setKnownMeetingIds(List.of("meeting-a"));

    MeetingRoleResolver resolver = resolver(properties);

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

    MeetingRoleResolver resolver = resolver(properties);

    MeetingRole resolvedRole = resolver.resolve("meeting-a", "u-participant");

    assertThat(resolvedRole).isEqualTo(MeetingRole.PARTICIPANT);
  }

  @Test
  void denyPolicyRejectsJoinWhenAssignmentMissing() {
    MeetingTokenProperties properties = new MeetingTokenProperties();
    properties.setUnknownRolePolicy("deny-access");
    properties.setKnownMeetingIds(List.of("meeting-a"));

    MeetingRoleResolver resolver = resolver(properties);

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

    MeetingRoleResolver resolver = resolver(properties);

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

    MeetingRoleResolver resolver = resolver(properties);

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

    MeetingRoleResolver resolver = resolver(properties);

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

    MeetingRoleResolver resolver = resolver(properties);

    assertThatThrownBy(() -> resolver.resolve("meeting-a", "u-conflict"))
        .isInstanceOf(MeetingTokenException.class)
        .satisfies(ex -> {
          MeetingTokenException error = (MeetingTokenException) ex;
          assertThat(error.status()).isEqualTo(HttpStatus.CONFLICT);
          assertThat(error.errorCode()).isEqualTo(ErrorCode.ROLE_MISMATCH.code());
        });
  }

  @Test
  void persistedRoleWinsOverConflictingConfiguredAssignments() {
    MeetingTokenProperties properties = new MeetingTokenProperties();
    properties.setKnownMeetingIds(List.of("meeting-a"));
    properties.setAssignments(List.of(
        assignment("meeting-a", "u-host", "admin"),
        assignment("meeting-a", "u-host", "moderator")));
    MeetingParticipantAssignmentRepository assignments = mock(MeetingParticipantAssignmentRepository.class);
    when(assignments.findByMeetingIdAndSubjectId("meeting-a", "u-host"))
        .thenReturn(Optional.of(persistedAssignment(MeetingRole.HOST)));
    MeetingRoleResolver resolver = new MeetingRoleResolver(properties, mock(MeetingRepository.class), assignments);

    assertThat(resolver.resolve("meeting-a", "u-host")).isEqualTo(MeetingRole.HOST);
  }

  @Test
  void unknownMeetingRejectsPersistedAssignment() {
    MeetingTokenProperties properties = new MeetingTokenProperties();
    properties.setKnownMeetingIds(List.of("meeting-known"));
    MeetingParticipantAssignmentRepository assignments = mock(MeetingParticipantAssignmentRepository.class);
    when(assignments.findByMeetingIdAndSubjectId("meeting-a", "u-host"))
        .thenReturn(Optional.of(persistedAssignment(MeetingRole.HOST)));
    MeetingRoleResolver resolver = new MeetingRoleResolver(properties, mock(MeetingRepository.class), assignments);

    assertThatThrownBy(() -> resolver.resolve("meeting-a", "u-host"))
        .isInstanceOf(MeetingTokenException.class)
        .satisfies(ex -> {
          MeetingTokenException error = (MeetingTokenException) ex;
          assertThat(error.status()).isEqualTo(HttpStatus.NOT_FOUND);
          assertThat(error.errorCode()).isEqualTo(ErrorCode.MEETING_NOT_FOUND.code());
        });
  }

  @Test
  void persistedMeetingOutsideConfiguredIdsCanResolveRole() {
    MeetingTokenProperties properties = new MeetingTokenProperties();
    properties.setKnownMeetingIds(List.of("meeting-configured"));
    MeetingRepository meetings = mock(MeetingRepository.class);
    when(meetings.existsById("meeting-a")).thenReturn(true);
    MeetingParticipantAssignmentRepository assignments = mock(MeetingParticipantAssignmentRepository.class);
    when(assignments.findByMeetingIdAndSubjectId("meeting-a", "u-host"))
        .thenReturn(Optional.of(persistedAssignment(MeetingRole.HOST)));
    MeetingRoleResolver resolver = new MeetingRoleResolver(properties, meetings, assignments);

    assertThat(resolver.resolve("meeting-a", "u-host")).isEqualTo(MeetingRole.HOST);
  }

  @Test
  void emptyConfiguredIdsDoNotRequireMeetingLookup() {
    MeetingTokenProperties properties = new MeetingTokenProperties();
    properties.setAssignments(List.of(assignment("meeting-a", "u-host", " HOST ")));
    MeetingRepository meetings = mock(MeetingRepository.class);
    when(meetings.existsById(anyString())).thenThrow(new IllegalStateException("Meeting lookup unavailable"));
    MeetingRoleResolver resolver = new MeetingRoleResolver(properties, meetings, mock(MeetingParticipantAssignmentRepository.class));

    assertThat(resolver.resolve("meeting-a", "u-host")).isEqualTo(MeetingRole.HOST);
  }

  @Test
  void configuredRoleMatchesBothMeetingAndSubjectWhenPersistedRoleIsNull() {
    MeetingTokenProperties properties = new MeetingTokenProperties();
    properties.setKnownMeetingIds(List.of("meeting-a"));
    properties.setAssignments(List.of(
        assignment("meeting-other", "u-host", "moderator"),
        assignment("meeting-a", "u-other", "participant"),
        assignment("meeting-a", "u-host", "host")));
    MeetingParticipantAssignmentRepository assignments = mock(MeetingParticipantAssignmentRepository.class);
    when(assignments.findByMeetingIdAndSubjectId("meeting-a", "u-host"))
        .thenReturn(Optional.of(persistedAssignment(null)));
    MeetingRoleResolver resolver = new MeetingRoleResolver(properties, mock(MeetingRepository.class), assignments);

    assertThat(resolver.resolve("meeting-a", "u-host")).isEqualTo(MeetingRole.HOST);
  }

  private static MeetingTokenProperties.RoleAssignment assignment(String meetingId, String subject, String role) {
    MeetingTokenProperties.RoleAssignment assignment = new MeetingTokenProperties.RoleAssignment();
    assignment.setMeetingId(meetingId);
    assignment.setSubject(subject);
    assignment.setRole(role);
    return assignment;
  }

  private static MeetingParticipantAssignment persistedAssignment(MeetingRole role) {
    java.time.Instant now = java.time.Instant.parse("2026-09-30T00:00:00Z");
    return new MeetingParticipantAssignment("assignment-1", "meeting-a", "u-host", role,
        now, "admin", now, now);
  }

  @Test
  void blockedSubjectWinsOverUnknownMeeting() {
    MeetingTokenProperties properties = new MeetingTokenProperties();
    properties.setKnownMeetingIds(List.of("meeting-a"));
    properties.setBlockedSubjects(Set.of("u-blocked"));

    MeetingRoleResolver resolver = resolver(properties);

    assertThatThrownBy(() -> resolver.resolve("meeting-missing", "u-blocked"))
        .isInstanceOf(MeetingTokenException.class)
        .satisfies(ex -> {
          MeetingTokenException error = (MeetingTokenException) ex;
          assertThat(error.status()).isEqualTo(HttpStatus.FORBIDDEN);
          assertThat(error.errorCode()).isEqualTo(ErrorCode.ACCESS_DENIED.code());
        });
  }

  private static MeetingRoleResolver resolver(MeetingTokenProperties properties) {
    MeetingRepository meetings = mock(MeetingRepository.class);
    when(meetings.existsById(anyString())).thenReturn(false);
    MeetingParticipantAssignmentRepository assignments = mock(MeetingParticipantAssignmentRepository.class);
    when(assignments.findByMeetingIdAndSubjectId(anyString(), anyString())).thenReturn(Optional.empty());
    return new MeetingRoleResolver(properties, meetings, assignments);
  }
}
