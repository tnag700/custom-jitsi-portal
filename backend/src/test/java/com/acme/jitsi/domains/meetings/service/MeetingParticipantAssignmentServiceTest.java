package com.acme.jitsi.domains.meetings.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.acme.jitsi.domains.meetings.event.MeetingParticipantAssignedEvent;
import com.acme.jitsi.domains.meetings.event.MeetingParticipantRemovedEvent;
import com.acme.jitsi.domains.meetings.event.MeetingParticipantRoleChangedEvent;
import java.time.Clock;
import java.time.Instant;
import java.sql.SQLIntegrityConstraintViolationException;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class MeetingParticipantAssignmentServiceTest {

  @Mock
  private MeetingParticipantAssignmentRepository assignmentRepository;

  @Mock
  private MeetingService meetingService;

  @Mock
  private ApplicationEventPublisher eventPublisher;

  private MeetingParticipantAssignmentService service;

  @BeforeEach
  void setUp() {
    service = new MeetingParticipantAssignmentService(
        assignmentRepository, meetingService, eventPublisher,
        Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
  }

  @Test
  void assignParticipant_createsNewAssignment() {
    String meetingId = "meeting-1";
    String subjectId = "user-1";
    String roleValue = "participant";
    String assignedBy = "admin-1";
    String traceId = "trace-1";

    Meeting meeting = Meeting.builder().meetingId(meetingId).roomId("room-1").build();
    when(meetingService.getMeeting(meetingId)).thenReturn(meeting);
    when(assignmentRepository.findByMeetingIdAndSubjectId(meetingId, subjectId))
        .thenReturn(Optional.empty());
    when(assignmentRepository.save(any(MeetingParticipantAssignment.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    MeetingParticipantAssignment result = service.assignParticipant(
        meetingId, subjectId, roleValue, assignedBy, traceId);

    assertThat(result.meetingId()).isEqualTo(meetingId);
    assertThat(result.subjectId()).isEqualTo(subjectId);
    assertThat(result.role()).isEqualTo(MeetingRole.PARTICIPANT);
    assertThat(result.assignedBy()).isEqualTo(assignedBy);
    verify(assignmentRepository).save(any(MeetingParticipantAssignment.class));

    verify(eventPublisher).publishEvent(any(MeetingParticipantAssignedEvent.class));
  }

  @Test
  void assignParticipant_updatesExistingAssignment() {
    String meetingId = "meeting-1";
    String subjectId = "user-1";
    String oldRoleValue = "participant";
    String newRoleValue = "moderator";
    String assignedBy = "admin-1";
    String traceId = "trace-1";

    Meeting meeting = Meeting.builder().meetingId(meetingId).roomId("room-1").build();
    when(meetingService.getMeeting(meetingId)).thenReturn(meeting);

    MeetingParticipantAssignment existing = MeetingParticipantAssignment.builder()
        .meetingId(meetingId)
        .subjectId(subjectId)
        .role(MeetingRole.PARTICIPANT)
        .assignedBy("old-admin")
        .assignedAt(Instant.now().minusSeconds(3600))
        .createdAt(Instant.now().minusSeconds(3600))
        .updatedAt(Instant.now().minusSeconds(3600))
        .build();

    when(assignmentRepository.findByMeetingIdAndSubjectId(meetingId, subjectId))
        .thenReturn(Optional.of(existing));
    when(assignmentRepository.save(any(MeetingParticipantAssignment.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    MeetingParticipantAssignment result = service.assignParticipant(
        meetingId, subjectId, newRoleValue, assignedBy, traceId);

    assertThat(result.role()).isEqualTo(MeetingRole.MODERATOR);
    assertThat(result.assignedBy()).isEqualTo(assignedBy);

    verify(eventPublisher).publishEvent(any(MeetingParticipantRoleChangedEvent.class));
  }

  @Test
  void updateAssignment_changesRole() {
    String meetingId = "meeting-1";
    String subjectId = "user-1";
    String newRoleValue = "host";
    String updatedBy = "admin-2";
    String traceId = "trace-2";

    Meeting meeting = Meeting.builder().meetingId(meetingId).roomId("room-1").build();
    when(meetingService.getMeeting(meetingId)).thenReturn(meeting);

    MeetingParticipantAssignment existing = MeetingParticipantAssignment.builder()
        .meetingId(meetingId)
        .subjectId(subjectId)
        .role(MeetingRole.PARTICIPANT)
        .assignedBy("admin-1")
        .assignedAt(Instant.now().minusSeconds(3600))
        .createdAt(Instant.now().minusSeconds(3600))
        .updatedAt(Instant.now().minusSeconds(3600))
        .build();

    when(assignmentRepository.findByMeetingIdAndSubjectId(meetingId, subjectId))
        .thenReturn(Optional.of(existing));
    when(assignmentRepository.save(any(MeetingParticipantAssignment.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    MeetingParticipantAssignment result = service.updateAssignment(
        meetingId, subjectId, newRoleValue, updatedBy, traceId);

    assertThat(result.role()).isEqualTo(MeetingRole.HOST);
    assertThat(result.assignedBy()).isEqualTo(updatedBy);

    verify(eventPublisher).publishEvent(any(MeetingParticipantRoleChangedEvent.class));
  }

  @Test
  void updateAssignment_throwsWhenNotFound() {
    String meetingId = "meeting-1";
    String subjectId = "user-1";
    String role = "host";
    String updatedBy = "admin-1";
    String traceId = "trace-1";

    Meeting meeting = Meeting.builder().meetingId(meetingId).roomId("room-1").build();
    when(meetingService.getMeeting(meetingId)).thenReturn(meeting);
    when(assignmentRepository.findByMeetingIdAndSubjectId(meetingId, subjectId))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.updateAssignment(
        meetingId, subjectId, role, updatedBy, traceId))
        .isInstanceOf(MeetingAssignmentNotFoundException.class);
  }

  @Test
  void unassignParticipant_removesAssignment() {
    String meetingId = "meeting-1";
    String subjectId = "user-1";
    String unassignedBy = "admin-1";
    String traceId = "trace-1";

    Meeting meeting = Meeting.builder().meetingId(meetingId).roomId("room-1").build();
    when(meetingService.getMeeting(meetingId)).thenReturn(meeting);

    MeetingParticipantAssignment existing = MeetingParticipantAssignment.builder()
        .meetingId(meetingId)
        .subjectId(subjectId)
        .role(MeetingRole.HOST)
        .assignedBy("admin-2")
        .assignedAt(Instant.now().minusSeconds(3600))
        .createdAt(Instant.now().minusSeconds(3600))
        .updatedAt(Instant.now().minusSeconds(3600))
        .build();

    when(assignmentRepository.findByMeetingIdAndSubjectId(meetingId, subjectId))
        .thenReturn(Optional.of(existing));

    service.unassignParticipant(meetingId, subjectId, unassignedBy, traceId);

    verify(assignmentRepository).delete(existing);

    verify(eventPublisher).publishEvent(any(MeetingParticipantRemovedEvent.class));
  }

  @Test
  void getAssignmentsByMeeting_returnsAllAssignments() {
    String meetingId = "meeting-1";

    Meeting meeting = Meeting.builder().meetingId(meetingId).roomId("room-1").build();
    when(meetingService.getMeeting(meetingId)).thenReturn(meeting);

    List<MeetingParticipantAssignment> assignments = List.of(
        MeetingParticipantAssignment.builder()
            .meetingId(meetingId)
            .subjectId("user-1")
            .role(MeetingRole.HOST)
            .assignedBy("admin-1")
            .assignedAt(Instant.now())
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build(),
        MeetingParticipantAssignment.builder()
            .meetingId(meetingId)
            .subjectId("user-2")
            .role(MeetingRole.PARTICIPANT)
            .assignedBy("admin-1")
            .assignedAt(Instant.now())
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build()
    );

    when(assignmentRepository.findByMeetingId(meetingId)).thenReturn(assignments);

    List<MeetingParticipantAssignment> result = service.getAssignmentsByMeeting(meetingId);

    assertThat(result).hasSize(2);
    assertThat(result).extracting(MeetingParticipantAssignment::subjectId)
        .containsExactlyInAnyOrder("user-1", "user-2");
  }

  @Test
  void assignParticipant_invalidRole_throwsException() {
    String meetingId = "meeting-1";
    String subjectId = "user-1";
    String invalidRole = "superadmin";
    String assignedBy = "admin-1";
    String traceId = "trace-1";

    Meeting meeting = Meeting.builder().meetingId(meetingId).roomId("room-1").build();
    when(meetingService.getMeeting(meetingId)).thenReturn(meeting);

    assertThatThrownBy(() -> service.assignParticipant(
        meetingId, subjectId, invalidRole, assignedBy, traceId))
        .isInstanceOf(MeetingInvalidRoleException.class);
  }

  @Test
  void assignParticipant_secondHost_throwsRoleConflictException() {
    String meetingId = "meeting-1";
    String newHostId = "user-host-new";
    String assignedBy = "admin-1";
    String traceId = "trace-conflict";

    Meeting meeting = Meeting.builder().meetingId(meetingId).roomId("room-1").build();
    when(meetingService.getMeeting(meetingId)).thenReturn(meeting);
    when(assignmentRepository.findByMeetingIdAndSubjectId(meetingId, newHostId))
        .thenReturn(Optional.empty());
    when(assignmentRepository.save(any(MeetingParticipantAssignment.class)))
        .thenThrow(new DataIntegrityViolationException("duplicate key uk_meeting_single_host"));

    assertThatThrownBy(() -> service.assignParticipant(
        meetingId, newHostId, "host", assignedBy, traceId))
        .isInstanceOf(MeetingRoleConflictException.class);
  }

  @Test
  void assignParticipant_hostUniqueSqlState_throwsRoleConflictException() {
    String meetingId = "meeting-1";
    String subjectId = "user-host-new";
    String assignedBy = "admin-1";
    String traceId = "trace-conflict";

    Meeting meeting = Meeting.builder().meetingId(meetingId).roomId("room-1").build();
    when(meetingService.getMeeting(meetingId)).thenReturn(meeting);
    when(assignmentRepository.findByMeetingIdAndSubjectId(meetingId, subjectId))
        .thenReturn(Optional.empty());
    when(assignmentRepository.save(any(MeetingParticipantAssignment.class)))
        .thenThrow(new DataIntegrityViolationException(
            "duplicate key",
            new SQLIntegrityConstraintViolationException("duplicate key", "23505")));

    assertThatThrownBy(() -> service.assignParticipant(
        meetingId, subjectId, "host", assignedBy, traceId))
        .isInstanceOf(MeetingRoleConflictException.class);
  }

  @Test
  void assignParticipant_nonHostUniqueSqlState_rethrowsDataIntegrityViolation() {
    String meetingId = "meeting-1";
    String subjectId = "user-2";
    String assignedBy = "admin-1";
    String traceId = "trace-conflict";

    Meeting meeting = Meeting.builder().meetingId(meetingId).roomId("room-1").build();
    when(meetingService.getMeeting(meetingId)).thenReturn(meeting);
    when(assignmentRepository.findByMeetingIdAndSubjectId(meetingId, subjectId))
        .thenReturn(Optional.empty());
    when(assignmentRepository.save(any(MeetingParticipantAssignment.class)))
        .thenThrow(new DataIntegrityViolationException(
            "duplicate key",
            new SQLIntegrityConstraintViolationException("duplicate key", "23505")));

    assertThatThrownBy(() -> service.assignParticipant(
        meetingId, subjectId, "participant", assignedBy, traceId))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void bulkAssignParticipants_multipleHosts_throwsException() {
    String meetingId = "meeting-1";
    Meeting meeting = Meeting.builder().meetingId(meetingId).roomId("room-1").build();
    when(meetingService.getMeeting(meetingId)).thenReturn(meeting);

    List<MeetingParticipantAssignmentService.BulkParticipantEntry> entries = List.of(
        new MeetingParticipantAssignmentService.BulkParticipantEntry("user-1", "host"),
        new MeetingParticipantAssignmentService.BulkParticipantEntry("user-2", "host")
    );

    assertThatThrownBy(() -> service.bulkAssignParticipants(meetingId, entries, "admin-1", "trace-1"))
        .isInstanceOf(BulkAssignmentValidationException.class)
        .hasMessageContaining("Multiple hosts specified");
        
    verify(assignmentRepository, never()).save(any());
  }

  @Test
  void assignParticipant_concurrentAssignment_recoversFromDataIntegrityViolation() {
    String meetingId = "meeting-1";
    String subjectId = "user-1";
    String roleValue = "participant";
    String assignedBy = "admin-1";
    String traceId = "trace-1";

    Meeting meeting = Meeting.builder().meetingId(meetingId).roomId("room-1").build();
    when(meetingService.getMeeting(meetingId)).thenReturn(meeting);
    
    // First call returns empty (no assignment yet)
    when(assignmentRepository.findByMeetingIdAndSubjectId(meetingId, subjectId))
        .thenReturn(Optional.empty())
        // Second call (during recovery) returns the assignment created by another thread
        .thenReturn(Optional.of(MeetingParticipantAssignment.builder()
            .assignmentId("concurrent-id")
            .meetingId(meetingId)
            .subjectId(subjectId)
            .role(MeetingRole.PARTICIPANT)
            .assignedBy("other-admin")
            .assignedAt(Instant.now().minusSeconds(10))
            .createdAt(Instant.now().minusSeconds(10))
            .updatedAt(Instant.now().minusSeconds(10))
            .build()));
            
    // First save throws exception (simulating race condition)
    // Second save succeeds
    when(assignmentRepository.save(any(MeetingParticipantAssignment.class)))
        .thenThrow(new DataIntegrityViolationException("duplicate key"))
        .thenAnswer(inv -> inv.getArgument(0));

    MeetingParticipantAssignment result = service.assignParticipant(
        meetingId, subjectId, roleValue, assignedBy, traceId);

    assertThat(result.meetingId()).isEqualTo(meetingId);
    assertThat(result.subjectId()).isEqualTo(subjectId);
    assertThat(result.role()).isEqualTo(MeetingRole.PARTICIPANT);
    assertThat(result.assignedBy()).isEqualTo(assignedBy);
    
    // Verify save was called twice
    verify(assignmentRepository, times(2)).save(any(MeetingParticipantAssignment.class));
    
    // Verify it published a role changed event (since it recovered an existing assignment)
    verify(eventPublisher).publishEvent(any(MeetingParticipantRoleChangedEvent.class));
  }
}
