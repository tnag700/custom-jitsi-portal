package com.acme.jitsi.domains.meetings.api;

import com.acme.jitsi.domains.meetings.service.Meeting;
import com.acme.jitsi.domains.meetings.service.MeetingParticipantAssignment;
import com.acme.jitsi.domains.meetings.service.MeetingParticipantAssignmentService;
import com.acme.jitsi.domains.meetings.service.MeetingProfileSnapshot;
import com.acme.jitsi.domains.meetings.service.MeetingProfilesPort;
import com.acme.jitsi.domains.meetings.service.MeetingRoomsPort;
import com.acme.jitsi.security.ProblemResponseFacade;
import com.acme.jitsi.security.TenantAccessGuard;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(version = "v1")
class MeetingParticipantAssignmentsController {

  private final MeetingParticipantAssignmentService assignmentService;
  private final MeetingRoomsPort meetingRoomsPort;
  private final TenantAccessGuard tenantAccessGuard;
  private final ProblemResponseFacade problemResponseFacade;
  private final MeetingProfilesPort meetingProfilesPort;

  MeetingParticipantAssignmentsController(
      MeetingParticipantAssignmentService assignmentService,
      MeetingRoomsPort meetingRoomsPort,
      TenantAccessGuard tenantAccessGuard,
      ProblemResponseFacade problemResponseFacade,
      MeetingProfilesPort meetingProfilesPort) {
    this.assignmentService = assignmentService;
    this.meetingRoomsPort = meetingRoomsPort;
    this.tenantAccessGuard = tenantAccessGuard;
    this.problemResponseFacade = problemResponseFacade;
    this.meetingProfilesPort = meetingProfilesPort;
  }

  @GetMapping("/meetings/{meetingId}/participants")
  List<ParticipantAssignmentResponse> listParticipants(
      @PathVariable("meetingId") String meetingId,
      @AuthenticationPrincipal OAuth2User principal) {
    
    Meeting meeting = validateTenantAccess(meetingId, principal);
    
    List<MeetingParticipantAssignment> assignments = assignmentService.getAssignmentsByMeeting(meeting);

    List<String> subjectIds = assignments.stream()
        .map(MeetingParticipantAssignment::subjectId)
        .toList();
    Map<String, MeetingProfileSnapshot> profilesBySubjectId = meetingProfilesPort.findBySubjectIds(subjectIds);

    return assignments.stream()
        .map(a -> {
        MeetingProfileSnapshot profile = profilesBySubjectId.get(a.subjectId());
          if (profile != null) {
            return ParticipantAssignmentResponse.fromDomainWithProfile(
                a, profile.fullName(), profile.organization(), profile.position());
          }
          return ParticipantAssignmentResponse.fromDomain(a);
        })
        .toList();
  }

  @PostMapping("/meetings/{meetingId}/participants")
  ResponseEntity<ParticipantAssignmentResponse> assignParticipant(
      @PathVariable("meetingId") String meetingId,
      @Valid @RequestBody AssignParticipantRequest request,
      @AuthenticationPrincipal OAuth2User principal,
      HttpServletRequest httpRequest) {

    String traceId = problemResponseFacade.resolveTraceId(httpRequest);
    String subject = principal.getName();
    
    Meeting meeting = validateTenantAccess(meetingId, principal);

    MeetingParticipantAssignment assignment = assignmentService.assignParticipant(
        meeting,
        request.subjectId(),
        request.role(),
        subject,
        traceId);

    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ParticipantAssignmentResponse.fromDomain(assignment));
  }

  @PutMapping("/meetings/{meetingId}/participants/{subjectId}")
  ResponseEntity<ParticipantAssignmentResponse> updateParticipant(
      @PathVariable("meetingId") String meetingId,
      @PathVariable("subjectId") String subjectId,
      @Valid @RequestBody UpdateParticipantRoleRequest request,
      @AuthenticationPrincipal OAuth2User principal,
      HttpServletRequest httpRequest) {

    String traceId = problemResponseFacade.resolveTraceId(httpRequest);
    String subject = principal.getName();
    
    Meeting meeting = validateTenantAccess(meetingId, principal);

    MeetingParticipantAssignment assignment = assignmentService.updateAssignment(
        meeting,
        subjectId,
        request.role(),
        subject,
        traceId);

    return ResponseEntity.ok(ParticipantAssignmentResponse.fromDomain(assignment));
  }

  @DeleteMapping("/meetings/{meetingId}/participants/{subjectId}")
  ResponseEntity<Void> unassignParticipant(
      @PathVariable("meetingId") String meetingId,
      @PathVariable("subjectId") String subjectId,
      @AuthenticationPrincipal OAuth2User principal,
      HttpServletRequest httpRequest) {

    String traceId = problemResponseFacade.resolveTraceId(httpRequest);
    String subject = principal.getName();
    
    Meeting meeting = validateTenantAccess(meetingId, principal);

    assignmentService.unassignParticipant(meeting, subjectId, subject, traceId);

    return ResponseEntity.noContent().build();
  }

  @PostMapping("/meetings/{meetingId}/participants/bulk")
  ResponseEntity<List<ParticipantAssignmentResponse>> bulkAssignParticipants(
      @PathVariable("meetingId") String meetingId,
      @Valid @RequestBody BulkAssignParticipantsRequest request,
      @AuthenticationPrincipal OAuth2User principal,
      HttpServletRequest httpRequest) {

    String traceId = problemResponseFacade.resolveTraceId(httpRequest);
    String assignedBy = principal.getName();
    
    Meeting meeting = validateTenantAccess(meetingId, principal);

    String defaultRole = request.defaultRole() != null ? request.defaultRole() : "participant";

    List<MeetingParticipantAssignmentService.BulkParticipantEntry> entries = request.participants().stream()
        .map(p -> new MeetingParticipantAssignmentService.BulkParticipantEntry(
            p.subjectId(),
            p.role() != null && !p.role().isBlank() ? p.role() : defaultRole))
        .toList();

    List<ParticipantAssignmentResponse> results = assignmentService.bulkAssignParticipants(
            meeting, entries, assignedBy, traceId).stream()
        .map(ParticipantAssignmentResponse::fromDomain)
        .toList();

    return ResponseEntity.status(HttpStatus.CREATED).body(results);
  }

  /**
   * Validates that the authenticated principal's tenant matches the meeting's room tenant.
   * Returns the loaded Meeting to avoid re-fetching it in the handler.
   */
  private Meeting validateTenantAccess(String meetingId, OAuth2User principal) {
    Meeting meeting = assignmentService.getMeeting(meetingId);
    tenantAccessGuard.assertAccess(meetingRoomsPort.getRequiredRoom(meeting.roomId()).tenantId(), principal);

    return meeting;
  }
}
