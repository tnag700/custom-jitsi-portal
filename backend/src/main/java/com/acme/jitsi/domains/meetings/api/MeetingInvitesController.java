package com.acme.jitsi.domains.meetings.api;

import com.acme.jitsi.domains.meetings.service.BulkInviteRecipient;
import com.acme.jitsi.domains.meetings.service.BulkInviteRequest;
import com.acme.jitsi.domains.meetings.service.BulkInviteResult;
import com.acme.jitsi.domains.meetings.service.BulkInviteValidationException;
import com.acme.jitsi.domains.meetings.service.DuplicateHandlingPolicy;
import com.acme.jitsi.domains.meetings.service.Meeting;
import com.acme.jitsi.domains.meetings.service.MeetingInvite;
import com.acme.jitsi.domains.meetings.service.MeetingInviteService;
import com.acme.jitsi.domains.meetings.service.MeetingRoomsPort;
import com.acme.jitsi.domains.meetings.service.MeetingService;
import com.acme.jitsi.domains.meetings.usecase.CreateBulkInvitesCommand;
import com.acme.jitsi.domains.meetings.usecase.CreateBulkInvitesUseCase;
import com.acme.jitsi.domains.meetings.usecase.CreateInviteCommand;
import com.acme.jitsi.domains.meetings.usecase.CreateInviteUseCase;
import com.acme.jitsi.domains.meetings.usecase.RevokeInviteCommand;
import com.acme.jitsi.domains.meetings.usecase.RevokeInviteUseCase;
import com.acme.jitsi.infrastructure.idempotency.Idempotent;
import com.acme.jitsi.security.ProblemResponseFacade;
import com.acme.jitsi.security.TenantAccessGuard;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(version = "v1")
class MeetingInvitesController {

  private final MeetingInviteService inviteService;
  private final CreateInviteUseCase createInviteUseCase;
  private final CreateBulkInvitesUseCase createBulkInvitesUseCase;
  private final RevokeInviteUseCase revokeInviteUseCase;
  private final MeetingService meetingService;
  private final MeetingRoomsPort meetingRoomsPort;
  private final TenantAccessGuard tenantAccessGuard;
  private final ProblemResponseFacade problemResponseFacade;
  private final Clock clock;

  MeetingInvitesController(
      MeetingInviteService inviteService,
      CreateInviteUseCase createInviteUseCase,
      CreateBulkInvitesUseCase createBulkInvitesUseCase,
      RevokeInviteUseCase revokeInviteUseCase,
      MeetingService meetingService,
      MeetingRoomsPort meetingRoomsPort,
      TenantAccessGuard tenantAccessGuard,
      ProblemResponseFacade problemResponseFacade,
      Clock clock) {
    this.inviteService = inviteService;
    this.createInviteUseCase = createInviteUseCase;
    this.createBulkInvitesUseCase = createBulkInvitesUseCase;
    this.revokeInviteUseCase = revokeInviteUseCase;
    this.meetingService = meetingService;
    this.meetingRoomsPort = meetingRoomsPort;
    this.tenantAccessGuard = tenantAccessGuard;
    this.problemResponseFacade = problemResponseFacade;
    this.clock = clock;
  }

  @Idempotent
  @PostMapping("/meetings/{meetingId}/invites")
  ResponseEntity<InviteResponse> createInvite(
      @PathVariable("meetingId") String meetingId,
      @Valid @RequestBody CreateInviteRequest request,
      @AuthenticationPrincipal OAuth2User principal,
      HttpServletRequest httpRequest) {
    assertTenantAccess(meetingId, principal);

    String traceId = problemResponseFacade.resolveTraceId(httpRequest);
    String actorId = principal.getName();

    Instant expiresAt = null;
    if (request.expiresInHours() != null && request.expiresInHours() > 0) {
      expiresAt = Instant.now(clock).plus(request.expiresInHours(), ChronoUnit.HOURS);
    }

    MeetingInvite invite = createInviteUseCase.execute(
      new CreateInviteCommand(meetingId, request.role(), request.maxUses(), expiresAt, actorId, traceId));

    return ResponseEntity.status(HttpStatus.CREATED).body(InviteResponse.fromDomain(invite));
  }

  @GetMapping("/meetings/{meetingId}/invites")
  ResponseEntity<PagedInviteResponse> listInvites(
      @PathVariable("meetingId") String meetingId,
      @RequestParam(name = "page", defaultValue = "0") int page,
      @RequestParam(name = "size", defaultValue = "20") int size,
      @AuthenticationPrincipal OAuth2User principal) {
    assertTenantAccess(meetingId, principal);

    int resolvedSize = (size <= 0) ? 20 : size;

    List<InviteResponse> items =
        inviteService.listByMeeting(meetingId, page, resolvedSize).stream().map(InviteResponse::fromDomain).toList();

    long totalElements = inviteService.countByMeeting(meetingId);
    int totalPages = (int) Math.ceil((double) totalElements / resolvedSize);

    return ResponseEntity.ok(new PagedInviteResponse(items, page, resolvedSize, totalElements, totalPages));
  }

  @Idempotent
  @PostMapping("/meetings/{meetingId}/invites/bulk")
  ResponseEntity<BulkInviteResponse> createBulkInvites(
      @PathVariable("meetingId") String meetingId,
      @Valid @RequestBody BulkCreateInviteRequest request,
      @AuthenticationPrincipal OAuth2User principal,
      HttpServletRequest httpRequest) {
    assertTenantAccess(meetingId, principal);

    String traceId = problemResponseFacade.resolveTraceId(httpRequest);
    String actorId = principal.getName();

    BulkInviteRequest serviceRequest =
        new BulkInviteRequest(
            request.recipients().stream()
                .map(
                    recipient ->
                        new BulkInviteRecipient(
                            recipient.rowIndex() == null ? 0 : recipient.rowIndex(),
                            recipient.email(),
                            recipient.userId(),
                            recipient.role()))
                .toList(),
            request.defaultRole(),
            request.defaultTtlMinutes(),
            request.defaultMaxUses(),
            request.duplicatePolicy() == null
                ? DuplicateHandlingPolicy.SKIP_EXISTING
                : request.duplicatePolicy());

    BulkInviteResult result = createBulkInvitesUseCase.execute(
      new CreateBulkInvitesCommand(meetingId, serviceRequest, actorId, traceId));

    if (!result.errors().isEmpty()) {
      throw new BulkInviteValidationException("Bulk invite partial failure", result.errors(), result);
    }

    return ResponseEntity.status(HttpStatus.CREATED).body(BulkInviteResponse.fromDomain(result));
  }

  @DeleteMapping("/meetings/{meetingId}/invites/{inviteId}")
  ResponseEntity<Void> revokeInvite(
      @PathVariable("meetingId") String meetingId,
      @PathVariable("inviteId") String inviteId,
      @AuthenticationPrincipal OAuth2User principal,
      HttpServletRequest httpRequest) {
    assertTenantAccess(meetingId, principal);

    String traceId = problemResponseFacade.resolveTraceId(httpRequest);
    String actorId = principal.getName();

    revokeInviteUseCase.execute(new RevokeInviteCommand(meetingId, inviteId, actorId, traceId));

    return ResponseEntity.noContent().build();
  }

  private void assertTenantAccess(String meetingId, OAuth2User principal) {
    Meeting meeting = meetingService.getMeeting(meetingId);
    tenantAccessGuard.assertAccess(meetingRoomsPort.getRequiredRoom(meeting.roomId()).tenantId(), principal);
  }
}
