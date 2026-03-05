package com.acme.jitsi.domains.meetings.service;

import com.acme.jitsi.domains.meetings.event.MeetingParticipantAssignedEvent;
import com.acme.jitsi.domains.meetings.event.MeetingParticipantRemovedEvent;
import com.acme.jitsi.domains.meetings.event.MeetingParticipantRoleChangedEvent;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.sql.SQLException;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MeetingParticipantAssignmentService {

  private static final Logger log = LoggerFactory.getLogger(MeetingParticipantAssignmentService.class);

  private final MeetingParticipantAssignmentRepository assignmentRepository;
  private final MeetingService meetingService;
  private final ApplicationEventPublisher eventPublisher;
  private final Clock clock;

  public MeetingParticipantAssignmentService(
      MeetingParticipantAssignmentRepository assignmentRepository,
      MeetingService meetingService,
      ApplicationEventPublisher eventPublisher,
      Clock clock) {
    this.assignmentRepository = assignmentRepository;
    this.meetingService = meetingService;
    this.eventPublisher = eventPublisher;
    this.clock = clock;
  }

  /**
   * Assign a participant to a meeting with a specific role.
   * If assignment already exists, updates the role (idempotent upsert).
   */
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

  /** Overload accepting a pre-loaded Meeting to avoid an extra DB lookup when caller already has it. */
  @Transactional
  public MeetingParticipantAssignment assignParticipant(
      Meeting meeting,
      String subjectId,
      String roleValue,
      String assignedBy,
      String traceId) {
    return assignParticipantInternal(meeting, subjectId, roleValue, assignedBy, traceId);
  }

  /** Internal upsert that accepts a pre-loaded Meeting to avoid redundant DB lookups. */
  private MeetingParticipantAssignment assignParticipantInternal(
      Meeting meeting,
      String subjectId,
      String roleValue,
      String assignedBy,
      String traceId) {

    String meetingId = meeting.meetingId();

    // Validate role is supported
    MeetingRole role = MeetingRole.from(roleValue)
        .orElseThrow(() -> new MeetingInvalidRoleException(
            "Role '" + roleValue + "' is not supported. Allowed values: host, moderator, participant"));

    // Check if assignment already exists (needed for role conflict exclusion)
    Optional<MeetingParticipantAssignment> existing = assignmentRepository.findByMeetingIdAndSubjectId(meetingId, subjectId);

    Instant now = Instant.now(clock);

    MeetingParticipantAssignment assignment;
    String action;

    if (existing.isPresent()) {
      // Update existing assignment
      MeetingParticipantAssignment old = existing.get();
      assignment = MeetingParticipantAssignment.builder()
          .assignmentId(old.assignmentId())
          .meetingId(meetingId)
          .subjectId(subjectId)
          .role(role)
          .assignedAt(now)
          .assignedBy(assignedBy)
          .createdAt(old.createdAt())
          .updatedAt(now)
          .build();
      action = "update";
      if (log.isInfoEnabled()) {
        log.info("assignment_updated meetingId={} subjectId={} oldRole={} newRole={} assignedBy={} traceId={}",
            meetingId, subjectId, old.role(), role, assignedBy, traceId);
      }
    } else {
      // Create new assignment
      assignment = MeetingParticipantAssignment.builder()
          .assignmentId(UUID.randomUUID().toString())
          .meetingId(meetingId)
          .subjectId(subjectId)
          .role(role)
          .assignedAt(now)
          .assignedBy(assignedBy)
          .createdAt(now)
          .updatedAt(now)
          .build();
      action = "assign";
      if (log.isInfoEnabled()) {
        log.info("assignment_created meetingId={} subjectId={} role={} assignedBy={} traceId={}",
            meetingId, subjectId, role, assignedBy, traceId);
      }
    }

    SaveOutcome saveOutcome = saveAssignmentWithRaceHandling(
        assignment,
        existing,
        meetingId,
        subjectId,
        role,
        assignedBy,
        now);
    MeetingParticipantAssignment saved = saveOutcome.saved();
    existing = saveOutcome.existing();

    // Record audit event
    String auditDetail = existing.isPresent()
      ? "subjectId:" + subjectId + ";role:" + existing.get().role().value() + "->" + role.value()
      : "subjectId:" + subjectId + ";role:none->" + role.value();
    
    publishAssignmentAuditEvent(existing, meeting, meetingId, assignedBy, traceId, auditDetail, subjectId);

    return saved;
  }

  private SaveOutcome saveAssignmentWithRaceHandling(
      MeetingParticipantAssignment assignment,
      Optional<MeetingParticipantAssignment> existing,
      String meetingId,
      String subjectId,
      MeetingRole role,
      String assignedBy,
      Instant now) {
    try {
      return new SaveOutcome(assignmentRepository.save(assignment), existing);
    } catch (DataIntegrityViolationException e) {
      if (isSingleHostConstraintViolation(e, role)) {
        throw new MeetingRoleConflictException(
            "Meeting '" + meetingId + "' already has a host. Only one host is allowed per meeting.",
            "ROLE_CONFLICT",
            e);
      }
      return saveAfterConcurrentCreate(assignment, existing, meetingId, subjectId, role, assignedBy, now, e);
    }
  }

  private SaveOutcome saveAfterConcurrentCreate(
      MeetingParticipantAssignment assignment,
      Optional<MeetingParticipantAssignment> existing,
      String meetingId,
      String subjectId,
      MeetingRole role,
      String assignedBy,
      Instant now,
      DataIntegrityViolationException originalError) {
    if (existing.isPresent()) {
      throw originalError;
    }

    Optional<MeetingParticipantAssignment> concurrentExisting = assignmentRepository.findByMeetingIdAndSubjectId(meetingId, subjectId);
    if (concurrentExisting.isEmpty()) {
      throw originalError;
    }

    MeetingParticipantAssignment old = concurrentExisting.get();
    assignment = MeetingParticipantAssignment.builder()
        .assignmentId(old.assignmentId())
        .meetingId(meetingId)
        .subjectId(subjectId)
        .role(role)
        .assignedAt(now)
        .assignedBy(assignedBy)
        .createdAt(old.createdAt())
        .updatedAt(now)
        .build();

    return new SaveOutcome(assignmentRepository.save(assignment), concurrentExisting);
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

  /**
   * Update an existing assignment with a new role.
   */
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

  /** Overload accepting a pre-loaded Meeting to avoid an extra DB lookup when caller already has it. */
  @Transactional
  public MeetingParticipantAssignment updateAssignment(
      Meeting meeting,
      String subjectId,
      String roleValue,
      String assignedBy,
      String traceId) {

    String meetingId = meeting.meetingId();

    // Validate role is supported
    MeetingRole role = MeetingRole.from(roleValue)
        .orElseThrow(() -> new MeetingInvalidRoleException(
            "Role '" + roleValue + "' is not supported. Allowed values: host, moderator, participant"));

    // Get existing assignment
    MeetingParticipantAssignment existing = assignmentRepository.findByMeetingIdAndSubjectId(meetingId, subjectId)
        .orElseThrow(() -> new MeetingAssignmentNotFoundException(meetingId, subjectId));

    Instant now = Instant.now(clock);
    MeetingParticipantAssignment updated = MeetingParticipantAssignment.builder()
        .assignmentId(existing.assignmentId())
        .meetingId(meetingId)
        .subjectId(subjectId)
        .role(role)
        .assignedAt(now)
        .assignedBy(assignedBy)
        .createdAt(existing.createdAt())
        .updatedAt(now)
        .build();

    if (log.isInfoEnabled()) {
      log.info("assignment_updated meetingId={} subjectId={} oldRole={} newRole={} assignedBy={} traceId={}",
        meetingId, subjectId, existing.role(), role, assignedBy, traceId);
    }

    MeetingParticipantAssignment saved;
    try {
      saved = assignmentRepository.save(updated);
    } catch (DataIntegrityViolationException e) {
      if (isSingleHostConstraintViolation(e, role)) {
        throw new MeetingRoleConflictException(
        "Meeting '" + meetingId + "' already has a host. Only one host is allowed per meeting.",
        "ROLE_CONFLICT",
        e);
      }
      throw e;
    }

    // Record audit event
    eventPublisher.publishEvent(new MeetingParticipantRoleChangedEvent(
        meetingId,
        meeting.roomId(),
        assignedBy,
        traceId,
        "subjectId:" + subjectId + ";role:" + existing.role().value() + "->" + role.value(),
        subjectId
    ));

    return saved;
  }

  /**
   * Remove a participant assignment from a meeting.
   */
  @Transactional
  public void unassignParticipant(
      String meetingId,
      String subjectId,
      String assignedBy,
      String traceId) {
    Meeting meeting = meetingService.getMeeting(meetingId);
    unassignParticipant(meeting, subjectId, assignedBy, traceId);
  }

  /** Overload accepting a pre-loaded Meeting to avoid an extra DB lookup when caller already has it. */
  @Transactional
  public void unassignParticipant(
      Meeting meeting,
      String subjectId,
      String assignedBy,
      String traceId) {

    String meetingId = meeting.meetingId();

    // Get existing assignment
    MeetingParticipantAssignment existing = assignmentRepository.findByMeetingIdAndSubjectId(meetingId, subjectId)
        .orElseThrow(() -> new MeetingAssignmentNotFoundException(meetingId, subjectId));

    assignmentRepository.delete(existing);

    if (log.isInfoEnabled()) {
      log.info("assignment_removed meetingId={} subjectId={} oldRole={} assignedBy={} traceId={}",
        meetingId, subjectId, existing.role(), assignedBy, traceId);
    }

    // Record audit event
    eventPublisher.publishEvent(new MeetingParticipantRemovedEvent(
        meetingId,
        meeting.roomId(),
        assignedBy,
        traceId,
        "subjectId:" + subjectId + ";role:" + existing.role().value() + "->none",
        subjectId
    ));
  }

  /**
   * Get all assignments for a meeting (validates meeting exists).
   */
  public List<MeetingParticipantAssignment> getAssignmentsByMeeting(String meetingId) {
    meetingService.getMeeting(meetingId);
    return assignmentRepository.findByMeetingId(meetingId);
  }

  /**
   * Get all assignments for a pre-loaded meeting (skips existence check).
   */
  public List<MeetingParticipantAssignment> getAssignmentsByMeeting(Meeting meeting) {
    return assignmentRepository.findByMeetingId(meeting.meetingId());
  }

  /** Delegates to MeetingService.getMeeting — use when controller only has assignmentService. */
  public Meeting getMeeting(String meetingId) {
    return meetingService.getMeeting(meetingId);
  }

  /**
   * Get assignment for a specific meeting and subject.
   */
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

  /** Overload accepting a pre-loaded Meeting to avoid an extra DB lookup when caller already has it. */
  @Transactional
  public List<MeetingParticipantAssignment> bulkAssignParticipants(
      Meeting meeting,
      List<BulkParticipantEntry> entries,
      String assignedBy,
      String traceId) {
    validateBulkHostConstraint(meeting, entries);

    java.util.ArrayList<MeetingParticipantAssignment> results = new java.util.ArrayList<>();
    for (int i = 0; i < entries.size(); i++) {
      BulkParticipantEntry entry = entries.get(i);
      try {
        results.add(assignParticipantInternal(meeting, entry.subjectId(), entry.role(), assignedBy, traceId));
      } catch (MeetingInvalidRoleException | MeetingRoleConflictException e) {
        throw toBulkValidationException(i, entry, e);
      }
    }
    return results;
  }

  private void validateBulkHostConstraint(Meeting meeting, List<BulkParticipantEntry> entries) {
    long hostCount = entries.stream()
        .filter(e -> MeetingRole.HOST.value().equalsIgnoreCase(e.role()))
        .count();

    if (hostCount > 1) {
      throw new BulkAssignmentValidationException(
          "Multiple hosts specified in bulk assignment. Only one host is allowed per meeting.",
          -1,
          null,
          "MEETING_ROLE_CONFLICT",
          null);
    }

    if (hostCount == 1) {
      validateSingleNewHost(meeting, entries);
    }
  }

  private void validateSingleNewHost(Meeting meeting, List<BulkParticipantEntry> entries) {
    boolean hasExistingHost = assignmentRepository.findByMeetingId(meeting.meetingId()).stream()
        .anyMatch(a -> a.role() == MeetingRole.HOST);
    if (!hasExistingHost) {
      return;
    }

    String newHostSubjectId = entries.stream()
        .filter(e -> MeetingRole.HOST.value().equalsIgnoreCase(e.role()))
        .findFirst()
        .map(BulkParticipantEntry::subjectId)
        .orElse(null);

    boolean isSameHost = assignmentRepository.findByMeetingIdAndSubjectId(meeting.meetingId(), newHostSubjectId)
        .map(a -> a.role() == MeetingRole.HOST)
        .orElse(false);

    if (!isSameHost) {
      throw new BulkAssignmentValidationException(
          "Meeting '" + meeting.meetingId() + "' already has a host. Only one host is allowed per meeting.",
          -1,
          null,
          "MEETING_ROLE_CONFLICT",
          null);
    }
  }

  private BulkAssignmentValidationException toBulkValidationException(
      int index,
      BulkParticipantEntry entry,
      RuntimeException exception) {
    String errorCode = exception instanceof MeetingInvalidRoleException invalid
        ? invalid.errorCode()
        : ((MeetingRoleConflictException) exception).errorCode();

    return new BulkAssignmentValidationException(
        "Failed to assign participant at index " + index + ": " + exception.getMessage(),
        index,
        entry.subjectId(),
        errorCode,
        exception);
  }

  private boolean isSingleHostConstraintViolation(DataIntegrityViolationException exception, MeetingRole role) {
    if (role != MeetingRole.HOST) {
      return false;
    }

    Throwable current = exception;
    while (current != null) {
      if (isConstraintNameMatch(current) || isSqlStateUniqueViolation(current) || containsHostConstraintMarker(current)) {
        return true;
      }

      current = current.getCause();
    }

    return false;
  }

  private boolean isConstraintNameMatch(Throwable throwable) {
    if (!(throwable instanceof ConstraintViolationException constraintViolationException)) {
      return false;
    }

    String constraintName = constraintViolationException.getConstraintName();
    return constraintName != null && constraintName.equalsIgnoreCase("uk_meeting_single_host");
  }

  private boolean isSqlStateUniqueViolation(Throwable throwable) {
    if (!(throwable instanceof SQLException sqlException)) {
      return false;
    }
    return "23505".equals(sqlException.getSQLState());
  }

  private boolean containsHostConstraintMarker(Throwable throwable) {
    String message = throwable.getMessage();
    return message != null && message.toLowerCase(Locale.ROOT).contains("uk_meeting_single_host");
  }

  private record SaveOutcome(
      MeetingParticipantAssignment saved,
      Optional<MeetingParticipantAssignment> existing) {
  }
}
