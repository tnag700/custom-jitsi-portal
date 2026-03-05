package com.acme.jitsi.domains.meetings.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class MeetingRoleResolutionPolicyTest {

  @Test
  void blockedSubjectPolicyRejectsBlockedSubject() {
    MeetingTokenProperties properties = new MeetingTokenProperties();
    properties.setBlockedSubjects(Set.of("u-blocked"));
    MeetingRoleResolutionContext context = new MeetingRoleResolutionContext("meeting-a", "u-blocked", properties);

    MeetingRoleResolutionPolicy policy = new BlockedSubjectMeetingRoleResolutionPolicy();

    assertThatThrownBy(() -> policy.resolve(context))
        .isInstanceOf(MeetingTokenException.class)
        .satisfies(ex -> {
          MeetingTokenException error = (MeetingTokenException) ex;
          assertThat(error.status()).isEqualTo(HttpStatus.FORBIDDEN);
          assertThat(error.errorCode()).isEqualTo("ACCESS_DENIED");
        });
  }

  @Test
  void blockedSubjectPolicySkipsAllowedSubject() {
    MeetingTokenProperties properties = new MeetingTokenProperties();
    properties.setBlockedSubjects(Set.of("u-blocked"));
    MeetingRoleResolutionContext context = new MeetingRoleResolutionContext("meeting-a", "u-allowed", properties);

    MeetingRoleResolutionPolicy policy = new BlockedSubjectMeetingRoleResolutionPolicy();

    assertThat(policy.resolve(context)).isEqualTo(Optional.empty());
  }

  @Test
  void unknownMeetingPolicyRejectsUnknownMeeting() {
    MeetingTokenProperties properties = new MeetingTokenProperties();
    properties.setKnownMeetingIds(List.of("meeting-a"));
    MeetingRoleResolutionContext context = new MeetingRoleResolutionContext("meeting-missing", "u-subject", properties);

    MeetingRepository meetingRepository = mock(MeetingRepository.class);
    when(meetingRepository.existsById(anyString())).thenReturn(false);
    MeetingRoleResolutionPolicy policy = new UnknownMeetingMeetingRoleResolutionPolicy(meetingRepository);

    assertThatThrownBy(() -> policy.resolve(context))
        .isInstanceOf(MeetingTokenException.class)
        .satisfies(ex -> {
          MeetingTokenException error = (MeetingTokenException) ex;
          assertThat(error.status()).isEqualTo(HttpStatus.NOT_FOUND);
          assertThat(error.errorCode()).isEqualTo("MEETING_NOT_FOUND");
        });
  }

  @Test
  void unknownMeetingPolicySkipsKnownMeeting() {
    MeetingTokenProperties properties = new MeetingTokenProperties();
    properties.setKnownMeetingIds(List.of("meeting-a"));
    MeetingRoleResolutionContext context = new MeetingRoleResolutionContext("meeting-a", "u-subject", properties);

    MeetingRepository meetingRepository = mock(MeetingRepository.class);
    when(meetingRepository.existsById(anyString())).thenReturn(false);
    MeetingRoleResolutionPolicy policy = new UnknownMeetingMeetingRoleResolutionPolicy(meetingRepository);

    assertThat(policy.resolve(context)).isEqualTo(Optional.empty());
  }

  @Test
  void explicitAssignmentPolicyReturnsSingleRole() {
    MeetingTokenProperties properties = new MeetingTokenProperties();
    properties.setAssignments(List.of(assignment("meeting-a", "u-host", "host")));
    MeetingRoleResolutionContext context = new MeetingRoleResolutionContext("meeting-a", "u-host", properties);

    MeetingRoleResolutionPolicy policy = new ExplicitAssignmentMeetingRoleResolutionPolicy();

    assertThat(policy.resolve(context)).contains(MeetingRole.HOST);
  }

  @Test
  void explicitAssignmentPolicyReturnsEmptyWhenAssignmentMissing() {
    MeetingTokenProperties properties = new MeetingTokenProperties();
    properties.setAssignments(List.of(assignment("meeting-a", "u-host", "host")));
    MeetingRoleResolutionContext context = new MeetingRoleResolutionContext("meeting-a", "u-participant", properties);

    MeetingRoleResolutionPolicy policy = new ExplicitAssignmentMeetingRoleResolutionPolicy();

    assertThat(policy.resolve(context)).isEqualTo(Optional.empty());
  }

  @Test
  void explicitAssignmentPolicyRejectsAmbiguousRoles() {
    MeetingTokenProperties properties = new MeetingTokenProperties();
    properties.setAssignments(List.of(
        assignment("meeting-a", "u-conflict", "host"),
        assignment("meeting-a", "u-conflict", "moderator")));
    MeetingRoleResolutionContext context = new MeetingRoleResolutionContext("meeting-a", "u-conflict", properties);

    MeetingRoleResolutionPolicy policy = new ExplicitAssignmentMeetingRoleResolutionPolicy();

    assertThatThrownBy(() -> policy.resolve(context))
        .isInstanceOf(MeetingTokenException.class)
        .satisfies(ex -> {
          MeetingTokenException error = (MeetingTokenException) ex;
          assertThat(error.status()).isEqualTo(HttpStatus.CONFLICT);
          assertThat(error.errorCode()).isEqualTo("ROLE_MISMATCH");
        });
  }

  @Test
  void explicitAssignmentPolicyRejectsUnsupportedConfiguredRole() {
    MeetingTokenProperties properties = new MeetingTokenProperties();
    properties.setAssignments(List.of(assignment("meeting-a", "u-host", "admin")));
    MeetingRoleResolutionContext context = new MeetingRoleResolutionContext("meeting-a", "u-host", properties);

    MeetingRoleResolutionPolicy policy = new ExplicitAssignmentMeetingRoleResolutionPolicy();

    assertThatThrownBy(() -> policy.resolve(context))
        .isInstanceOf(MeetingTokenException.class)
        .satisfies(ex -> {
          MeetingTokenException error = (MeetingTokenException) ex;
          assertThat(error.status()).isEqualTo(HttpStatus.CONFLICT);
          assertThat(error.errorCode()).isEqualTo("ROLE_MISMATCH");
        });
  }

  @Test
  void unknownRolePolicyReturnsParticipantForFallbackPolicy() {
    MeetingTokenProperties properties = new MeetingTokenProperties();
    properties.setUnknownRolePolicy("fallback-participant");
    MeetingRoleResolutionContext context = new MeetingRoleResolutionContext("meeting-a", "u-subject", properties);

    MeetingRoleResolutionPolicy policy = new UnknownRolePolicyMeetingRoleResolutionPolicy();

    assertThat(policy.resolve(context)).contains(MeetingRole.PARTICIPANT);
  }

  @Test
  void unknownRolePolicyRejectsWhenDenyPolicyConfigured() {
    MeetingTokenProperties properties = new MeetingTokenProperties();
    properties.setUnknownRolePolicy("deny-access");
    MeetingRoleResolutionContext context = new MeetingRoleResolutionContext("meeting-a", "u-subject", properties);

    MeetingRoleResolutionPolicy policy = new UnknownRolePolicyMeetingRoleResolutionPolicy();

    assertThatThrownBy(() -> policy.resolve(context))
        .isInstanceOf(MeetingTokenException.class)
        .satisfies(ex -> {
          MeetingTokenException error = (MeetingTokenException) ex;
          assertThat(error.status()).isEqualTo(HttpStatus.FORBIDDEN);
          assertThat(error.errorCode()).isEqualTo("ACCESS_DENIED");
        });
  }

  private MeetingTokenProperties.RoleAssignment assignment(String meetingId, String subject, String role) {
    MeetingTokenProperties.RoleAssignment assignment = new MeetingTokenProperties.RoleAssignment();
    assignment.setMeetingId(meetingId);
    assignment.setSubject(subject);
    assignment.setRole(role);
    return assignment;
  }
}