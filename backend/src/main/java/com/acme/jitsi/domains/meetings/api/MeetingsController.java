package com.acme.jitsi.domains.meetings.api;

import com.acme.jitsi.domains.meetings.service.Meeting;
import com.acme.jitsi.domains.meetings.service.MeetingRoomSnapshot;
import com.acme.jitsi.domains.meetings.service.MeetingRoomsPort;
import com.acme.jitsi.domains.meetings.service.MeetingService;
import com.acme.jitsi.domains.meetings.usecase.CancelMeetingCommand;
import com.acme.jitsi.domains.meetings.usecase.CancelMeetingUseCase;
import com.acme.jitsi.domains.meetings.usecase.CreateMeetingCommand;
import com.acme.jitsi.domains.meetings.usecase.CreateMeetingUseCase;
import com.acme.jitsi.domains.meetings.usecase.UpdateMeetingCommand;
import com.acme.jitsi.domains.meetings.usecase.UpdateMeetingUseCase;
import com.acme.jitsi.infrastructure.idempotency.Idempotent;
import com.acme.jitsi.security.ProblemResponseFacade;
import com.acme.jitsi.security.TenantAccessGuard;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(version = "v1")
class MeetingsController {

  private static final Logger log = LoggerFactory.getLogger(MeetingsController.class);

  private static final String MEETING_ALL_FIELDS =
      "title,description,meetingType,startsAt,endsAt,allowGuests,recordingEnabled";
  private static final String MEETING_STATUS_FIELD = "status";

  private final MeetingService meetingService;
  private final CreateMeetingUseCase createMeetingUseCase;
  private final UpdateMeetingUseCase updateMeetingUseCase;
  private final CancelMeetingUseCase cancelMeetingUseCase;
  private final MeetingRoomsPort meetingRoomsPort;
  private final ProblemResponseFacade problemResponseFacade;
  private final TenantAccessGuard tenantAccessGuard;

  MeetingsController(
      MeetingService meetingService,
      CreateMeetingUseCase createMeetingUseCase,
      UpdateMeetingUseCase updateMeetingUseCase,
      CancelMeetingUseCase cancelMeetingUseCase,
      MeetingRoomsPort meetingRoomsPort,
      ProblemResponseFacade problemResponseFacade,
      TenantAccessGuard tenantAccessGuard) {
    this.meetingService = meetingService;
    this.createMeetingUseCase = createMeetingUseCase;
    this.updateMeetingUseCase = updateMeetingUseCase;
    this.cancelMeetingUseCase = cancelMeetingUseCase;
    this.meetingRoomsPort = meetingRoomsPort;
    this.problemResponseFacade = problemResponseFacade;
    this.tenantAccessGuard = tenantAccessGuard;
  }

  @Idempotent
  @PostMapping("/rooms/{roomId}/meetings")
  ResponseEntity<MeetingResponse> createMeeting(
      @PathVariable("roomId") String roomId,
      @Valid @RequestBody CreateMeetingRequest request,
      @AuthenticationPrincipal OAuth2User principal,
      HttpServletRequest httpRequest) {
    String traceId = problemResponseFacade.resolveTraceId(httpRequest);
    String subject = principal.getName();
    MeetingRoomSnapshot room = meetingRoomsPort.getRequiredRoom(roomId);
    tenantAccessGuard.assertAccess(room.tenantId(), principal);

    Meeting meeting = createMeetingUseCase.execute(new CreateMeetingCommand(
      roomId,
      request.title(),
      request.description(),
      request.meetingType(),
      request.startsAt(),
      request.endsAt(),
      request.resolvedAllowGuests(),
      request.resolvedRecordingEnabled(),
      subject,
      traceId));

    if (log.isInfoEnabled()) {
      log.info(
        "meeting_created action=create roomId={} meetingId={} subject={} traceId={} changedFields={}",
        roomId,
        meeting.meetingId(),
        subject,
        traceId,
        MEETING_ALL_FIELDS);
    }

    return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(meeting));
  }

  @GetMapping("/rooms/{roomId}/meetings")
  PagedMeetingResponse listMeetings(
      @PathVariable("roomId") String roomId,
      @RequestParam(name = "page", defaultValue = "0") int page,
      @RequestParam(name = "size", defaultValue = "20") int size,
      @AuthenticationPrincipal OAuth2User principal) {
    MeetingRoomSnapshot room = meetingRoomsPort.getRequiredRoom(roomId);
    tenantAccessGuard.assertAccess(room.tenantId(), principal);

    int resolvedSize = (size <= 0) ? 20 : size;

    List<MeetingResponse> items = meetingService.listMeetings(roomId, page, resolvedSize).stream()
        .map(MeetingsController::toResponse)
        .toList();
    long totalElements = meetingService.countMeetings(roomId);
    int totalPages = (int) Math.ceil((double) totalElements / resolvedSize);
    return new PagedMeetingResponse(items, page, resolvedSize, totalElements, totalPages);
  }

  @GetMapping("/meetings/{meetingId}")
  MeetingResponse getMeeting(
      @PathVariable("meetingId") String meetingId,
      @AuthenticationPrincipal OAuth2User principal) {
    Meeting meeting = meetingService.getMeeting(meetingId);
    MeetingRoomSnapshot room = meetingRoomsPort.getRequiredRoom(meeting.roomId());
    tenantAccessGuard.assertAccess(room.tenantId(), principal);
    return toResponse(meeting);
  }

  @PutMapping("/meetings/{meetingId}")
  MeetingResponse updateMeeting(
      @PathVariable("meetingId") String meetingId,
      @Valid @RequestBody UpdateMeetingRequest request,
      @AuthenticationPrincipal OAuth2User principal,
      HttpServletRequest httpRequest) {
    String traceId = problemResponseFacade.resolveTraceId(httpRequest);
    String subject = principal.getName();

    Meeting oldMeeting = meetingService.getMeeting(meetingId);
    MeetingRoomSnapshot room = meetingRoomsPort.getRequiredRoom(oldMeeting.roomId());
    tenantAccessGuard.assertAccess(room.tenantId(), principal);

    Meeting meeting = updateMeetingUseCase.execute(new UpdateMeetingCommand(
      oldMeeting,
      request.title(),
      request.description(),
      request.meetingType(),
      request.startsAt(),
      request.endsAt(),
      request.allowGuests(),
      request.recordingEnabled(),
      subject,
      traceId));

    if (log.isInfoEnabled()) {
      log.info(
        "meeting_updated action=update roomId={} meetingId={} subject={} traceId={} oldStartsAt={} newStartsAt={} oldEndsAt={} newEndsAt={}",
        meeting.roomId(),
        meeting.meetingId(),
        subject,
        traceId,
        oldMeeting.startsAt(),
        meeting.startsAt(),
        oldMeeting.endsAt(),
        meeting.endsAt());
    }

    return toResponse(meeting);
  }

  @PostMapping("/meetings/{meetingId}/cancel")
  MeetingResponse cancelMeeting(
      @PathVariable("meetingId") String meetingId,
      @AuthenticationPrincipal OAuth2User principal,
      HttpServletRequest httpRequest) {
    String traceId = problemResponseFacade.resolveTraceId(httpRequest);
    String subject = principal.getName();

    Meeting oldMeeting = meetingService.getMeeting(meetingId);
    MeetingRoomSnapshot room = meetingRoomsPort.getRequiredRoom(oldMeeting.roomId());
    tenantAccessGuard.assertAccess(room.tenantId(), principal);

    Meeting meeting = cancelMeetingUseCase.execute(new CancelMeetingCommand(oldMeeting, subject, traceId));
    if (log.isInfoEnabled()) {
      log.info(
        "meeting_canceled action=cancel roomId={} meetingId={} subject={} traceId={} oldStatus={} newStatus={}",
        meeting.roomId(),
        meeting.meetingId(),
        subject,
        traceId,
        oldMeeting.status(),
        meeting.status());
    }

    return toResponse(meeting);
  }

  private static MeetingResponse toResponse(Meeting meeting) {
    return new MeetingResponse(
        meeting.meetingId(),
        meeting.roomId(),
        meeting.title(),
        meeting.description(),
        meeting.meetingType(),
        meeting.configSetId(),
        meeting.status().name().toLowerCase(),
        meeting.startsAt(),
        meeting.endsAt(),
        meeting.allowGuests(),
        meeting.recordingEnabled(),
        meeting.createdAt(),
        meeting.updatedAt());
  }
}