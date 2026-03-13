package com.acme.jitsi.domains.meetings.service;

import com.acme.jitsi.domains.meetings.event.MeetingParticipantAssignedEvent;
import com.acme.jitsi.domains.meetings.event.MeetingParticipantRemovedEvent;
import com.acme.jitsi.domains.meetings.event.MeetingParticipantRoleChangedEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MeetingParticipantAssignmentService {

  private static final Logger log = LoggerFactory.getLogger(MeetingParticipantAssignmentService.class);
  private final MeetingParticipantAssignmentRepository assignmentRepository;
  private final MeetingService meetingService;
  private final ApplicationEventPublisher eventPublisher;
  private final MeetingParticipantAssignmentFactory assignmentFactory;
  private final MeetingParticipantAssignmentSaveHelper saveHelper;
  private final MeetingParticipantBulkAssignmentValidator bulkAssignmentValidator;

  public MeetingParticipantAssignmentService(
      MeetingParticipantAssignmentRepository assignmentRepository,
      MeetingService meetingService,
      ApplicationEventPublisher eventPublisher,
      MeetingParticipantAssignmentFactory assignmentFactory,
      MeetingParticipantAssignmentSaveHelper saveHelper,
      MeetingParticipantBulkAssignmentValidator bulkAssignmentValidator) {
    this.assignmentRepository = assignmentRepository;
    this.meetingService = meetingService;
    this.eventPublisher = eventPublisher;
    this.assignmentFactory = assignmentFactory;
    this.saveHelper = saveHelper;
    this.bulkAssignmentValidator = bulkAssignmentValidator;
  }

  @Transactional
  public MeetingParticipantAssignment assignParticipant(
      String meetingId,
      String subjectId,
      String roleValue,
      String assignedBy,
      String traceId) {
    Meeting meeting = meetingService.getMeeting(meetingId);
    return assignParticipantInternal(meeting, subjectId, roleValue, assignedBy, traceId);
  }

  @Transactional
  public MeetingParticipantAssignment assignParticipant(
      Meeting meeting,
      String subjectId,
      String roleValue,
      String assignedBy,
      String traceId) {
    return assignParticipantInternal(meeting, subjectId, roleValue, assignedBy, traceId);
  }

  private MeetingParticipantAssignment assignParticipantInternal(
      Meeting meeting,
      String subjectId,
      String roleValue,
      String assignedBy,
      String traceId) {
    String meetingId = meeting.meetingId();

    MeetingRole role = assignmentFactory.resolveRole(roleValue);
    Optional<MeetingParticipantAssignment> existing =
        assignmentRepository.findByMeetingIdAndSubjectId(meetingId, subjectId);
    MeetingParticipantAssignment assignment =
        existing
            .map(current -> assignmentFactory.updatedAssignment(current, role, assignedBy))
            .orElseGet(() -> assignmentFactory.newAssignment(meetingId, subjectId, role, assignedBy));

    logAssignmentUpsert(meetingId, subjectId, assignedBy, traceId, role, existing.orElse(null));

    MeetingParticipantAssignmentSaveHelper.SaveOutcome saveOutcome =
        saveHelper.save(assignment, existing, meetingId, subjectId, role, assignedBy);
    MeetingParticipantAssignment saved = saveOutcome.saved();
    Optional<MeetingParticipantAssignment> savedExisting = saveOutcome.existing();
    String auditDetail =
        assignmentFactory.upsertAuditDetail(savedExisting.orElse(null), subjectId, role);

    publishAssignmentAuditEvent(
        savedExisting, meeting, meetingId, assignedBy, traceId, auditDetail, subjectId);

    return saved;
  }
  private void logAssignmentUpsert(
      String meetingId,
      String subjectId,
      String assignedBy,
      String traceId,
      MeetingRole role,
      MeetingParticipantAssignment existing) {
    if (existing == null) {
      if (log.isInfoEnabled()) {
        log.info(
            "assignment_created meetingId={} subjectId={} role={} assignedBy={} traceId={}",
            meetingId,
            subjectId,
            role,
            assignedBy,
            traceId);
      }
      return;
    }

    if (log.isInfoEnabled()) {
      log.info(
          "assignment_updated meetingId={} subjectId={} oldRole={} newRole={} assignedBy={} traceId={}",
          meetingId,
          subjectId,
          existing.role(),
          role,
          assignedBy,
          traceId);
    }
  }
  private void publishAssignmentAuditEvent(
      Optional<MeetingParticipantAssignment> existing,
      Meeting meeting,
      String meetingId,
      String assignedBy,
      String traceId,
      String auditDetail,
      String subjectId) {
    if (existing.isPresent()) {
      eventPublisher.publishEvent(new MeetingParticipantRoleChangedEvent(
          meetingId,
          meeting.roomId(),
          assignedBy,
          traceId,
          auditDetail,
          subjectId));
      return;
    }

    eventPublisher.publishEvent(new MeetingParticipantAssignedEvent(
        meetingId,
        meeting.roomId(),
        assignedBy,
        traceId,
        auditDetail,
        subjectId));
  }

  @Transactional
  public MeetingParticipantAssignment updateAssignment(
      String meetingId,
      String subjectId,
      String roleValue,
      String assignedBy,
      String traceId) {
    Meeting meeting = meetingService.getMeeting(meetingId);
    return updateAssignment(meeting, subjectId, roleValue, assignedBy, traceId);
  }

  @Transactional
  public MeetingParticipantAssignment updateAssignment(
      Meeting meeting,
      String subjectId,
      String roleValue,
      String assignedBy,
      String traceId) {
    String meetingId = meeting.meetingId();
    MeetingRole role = assignmentFactory.resolveRole(roleValue);
    MeetingParticipantAssignment existing = assignmentRepository.findByMeetingIdAndSubjectId(meetingId, subjectId)
        .orElseThrow(() -> new MeetingAssignmentNotFoundException(meetingId, subjectId));
    MeetingParticipantAssignment updated = assignmentFactory.updatedAssignment(existing, role, assignedBy);

    if (log.isInfoEnabled()) {
      log.info(
          "assignment_updated meetingId={} subjectId={} oldRole={} newRole={} assignedBy={} traceId={}",
          meetingId,
          subjectId,
          existing.role(),
          role,
          assignedBy,
          traceId);
    }

    MeetingParticipantAssignment saved =
        saveHelper.save(updated, Optional.of(existing), meetingId, subjectId, role, assignedBy).saved();

    eventPublisher.publishEvent(new MeetingParticipantRoleChangedEvent(
        meetingId,
        meeting.roomId(),
        assignedBy,
        traceId,
        assignmentFactory.upsertAuditDetail(existing, subjectId, role),
        subjectId
    ));

    return saved;
  }

  @Transactional
  public void unassignParticipant(
      String meetingId,
      String subjectId,
      String assignedBy,
      String traceId) {
    Meeting meeting = meetingService.getMeeting(meetingId);
    unassignParticipant(meeting, subjectId, assignedBy, traceId);
  }

  @Transactional
  public void unassignParticipant(
      Meeting meeting,
      String subjectId,
      String assignedBy,
      String traceId) {
    String meetingId = meeting.meetingId();
    MeetingParticipantAssignment existing = assignmentRepository.findByMeetingIdAndSubjectId(meetingId, subjectId)
        .orElseThrow(() -> new MeetingAssignmentNotFoundException(meetingId, subjectId));

    assignmentRepository.delete(existing);

    if (log.isInfoEnabled()) {
      log.info("assignment_removed meetingId={} subjectId={} oldRole={} assignedBy={} traceId={}",
        meetingId, subjectId, existing.role(), assignedBy, traceId);
    }

    eventPublisher.publishEvent(new MeetingParticipantRemovedEvent(
        meetingId,
        meeting.roomId(),
        assignedBy,
        traceId,
      assignmentFactory.removalAuditDetail(existing, subjectId),
        subjectId
    ));
  }

  public List<MeetingParticipantAssignment> getAssignmentsByMeeting(String meetingId) {
    meetingService.getMeeting(meetingId);
    return assignmentRepository.findByMeetingId(meetingId);
  }

  public List<MeetingParticipantAssignment> getAssignmentsByMeeting(Meeting meeting) {
    return assignmentRepository.findByMeetingId(meeting.meetingId());
  }

  public Meeting getMeeting(String meetingId) {
    return meetingService.getMeeting(meetingId);
  }

  public Optional<MeetingParticipantAssignment> getAssignment(String meetingId, String subjectId) {
    return assignmentRepository.findByMeetingIdAndSubjectId(meetingId, subjectId);
  }

  public record BulkParticipantEntry(String subjectId, String role) {}

  @Transactional
  public List<MeetingParticipantAssignment> bulkAssignParticipants(
      String meetingId,
      List<BulkParticipantEntry> entries,
      String assignedBy,
      String traceId) {
    Meeting meeting = meetingService.getMeeting(meetingId);
    return bulkAssignParticipants(meeting, entries, assignedBy, traceId);
  }

  @Transactional
  public List<MeetingParticipantAssignment> bulkAssignParticipants(
      Meeting meeting,
      List<BulkParticipantEntry> entries,
      String assignedBy,
      String traceId) {
    bulkAssignmentValidator.validate(meeting, entries);

    ArrayList<MeetingParticipantAssignment> results = new ArrayList<>();
    for (int i = 0; i < entries.size(); i++) {
      BulkParticipantEntry entry = entries.get(i);
      try {
        results.add(assignParticipantInternal(meeting, entry.subjectId(), entry.role(), assignedBy, traceId));
      } catch (MeetingInvalidRoleException | MeetingRoleConflictException e) {
        throw bulkAssignmentValidator.toBulkValidationException(i, entry, e);
      }
    }
    return results;
  }
}
