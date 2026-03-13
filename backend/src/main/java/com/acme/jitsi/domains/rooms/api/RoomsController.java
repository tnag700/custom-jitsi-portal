package com.acme.jitsi.domains.rooms.api;

import com.acme.jitsi.domains.rooms.service.Room;
import com.acme.jitsi.domains.rooms.service.RoomService;
import com.acme.jitsi.domains.rooms.service.RoomStatus;
import com.acme.jitsi.domains.rooms.usecase.CloseRoomCommand;
import com.acme.jitsi.domains.rooms.usecase.CloseRoomUseCase;
import com.acme.jitsi.domains.rooms.usecase.CreateRoomCommand;
import com.acme.jitsi.domains.rooms.usecase.CreateRoomUseCase;
import com.acme.jitsi.domains.rooms.usecase.DeleteRoomCommand;
import com.acme.jitsi.domains.rooms.usecase.DeleteRoomUseCase;
import com.acme.jitsi.domains.rooms.usecase.UpdateRoomCommand;
import com.acme.jitsi.domains.rooms.usecase.UpdateRoomUseCase;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/rooms", version = "v1")
class RoomsController {

  private static final Logger log = LoggerFactory.getLogger(RoomsController.class);

  private final RoomService roomService;
  private final CreateRoomUseCase createRoomUseCase;
  private final UpdateRoomUseCase updateRoomUseCase;
  private final CloseRoomUseCase closeRoomUseCase;
  private final DeleteRoomUseCase deleteRoomUseCase;
  private final ProblemResponseFacade problemResponseFacade;
  private final TenantAccessGuard tenantAccessGuard;

  RoomsController(
      RoomService roomService,
      CreateRoomUseCase createRoomUseCase,
      UpdateRoomUseCase updateRoomUseCase,
      CloseRoomUseCase closeRoomUseCase,
      DeleteRoomUseCase deleteRoomUseCase,
      ProblemResponseFacade problemResponseFacade,
      TenantAccessGuard tenantAccessGuard) {
    this.roomService = roomService;
    this.createRoomUseCase = createRoomUseCase;
    this.updateRoomUseCase = updateRoomUseCase;
    this.closeRoomUseCase = closeRoomUseCase;
    this.deleteRoomUseCase = deleteRoomUseCase;
    this.problemResponseFacade = problemResponseFacade;
    this.tenantAccessGuard = tenantAccessGuard;
  }

  @PostMapping
  ResponseEntity<RoomResponse> createRoom(
      @Valid @RequestBody CreateRoomRequest request,
      @AuthenticationPrincipal OAuth2User principal,
      HttpServletRequest httpRequest) {

    String traceId = problemResponseFacade.resolveTraceId(httpRequest);
    String subject = principal.getName();

    if (log.isInfoEnabled()) {
      log.info(
          "room_create_started name={} tenantId={} subject={} traceId={}",
          request.name(),
          request.tenantId(),
          subject,
          traceId);
    }

    Room room = createRoomUseCase.execute(new CreateRoomCommand(
        request.name(),
        request.description(),
        request.tenantId(),
        request.configSetId(),
        subject,
        traceId));

    if (log.isInfoEnabled()) {
      log.info(
          "room_created roomId={} name={} tenantId={} subject={} traceId={}",
          room.roomId(),
          room.name(),
          room.tenantId(),
          subject,
          traceId);
    }

    return ResponseEntity
        .status(HttpStatus.CREATED)
        .body(toResponse(room));
  }

  @GetMapping
  PagedRoomResponse listRooms(
      @RequestParam(name = "page", defaultValue = "0") int page,
      @RequestParam(name = "size", defaultValue = "20") int size,
      @RequestParam(name = "tenantId") String tenantId,
      @AuthenticationPrincipal OAuth2User principal,
      HttpServletRequest httpRequest) {

    tenantAccessGuard.assertAccess(tenantId, principal);

    String traceId = problemResponseFacade.resolveTraceId(httpRequest);

    int resolvedSize = (size <= 0) ? 20 : size;

    if (log.isInfoEnabled()) {
      log.info(
          "room_list_requested tenantId={} page={} size={} subject={} traceId={}",
          tenantId,
          page,
          resolvedSize,
          principal.getName(),
          traceId);
    }

    List<Room> rooms = roomService.listRooms(tenantId, page, resolvedSize);
    long totalElements = roomService.countRooms(tenantId);
    int totalPages = (int) Math.ceil((double) totalElements / resolvedSize);

    List<RoomResponse> items = rooms.stream()
        .map(RoomsController::toResponse)
        .toList();

    return new PagedRoomResponse(items, page, resolvedSize, totalElements, totalPages);
  }

  @GetMapping("/{roomId}")
  RoomResponse getRoom(
      @PathVariable("roomId") String roomId,
      @AuthenticationPrincipal OAuth2User principal,
      HttpServletRequest httpRequest) {

    String traceId = problemResponseFacade.resolveTraceId(httpRequest);

    if (log.isInfoEnabled()) {
      log.info(
          "room_get_requested roomId={} subject={} traceId={}",
          roomId,
          principal.getName(),
          traceId);
    }

    Room room = roomService.getRoom(roomId);
    tenantAccessGuard.assertAccess(room.tenantId(), principal);
    return toResponse(room);
  }

  @PutMapping("/{roomId}")
  RoomResponse updateRoom(
      @PathVariable("roomId") String roomId,
      @Valid @RequestBody UpdateRoomRequest request,
      @AuthenticationPrincipal OAuth2User principal,
      HttpServletRequest httpRequest) {

    String traceId = problemResponseFacade.resolveTraceId(httpRequest);
    String subject = principal.getName();

    // Get old room for audit logging (AC6: old/new values)
    Room oldRoom = roomService.getRoom(roomId);
    tenantAccessGuard.assertAccess(oldRoom.tenantId(), principal);

    if (log.isInfoEnabled()) {
      log.info(
          "room_update_started roomId={} subject={} traceId={}",
          roomId,
          subject,
          traceId);
    }

    Room room = updateRoomUseCase.execute(new UpdateRoomCommand(
        oldRoom,
        request.name(),
        request.description(),
        request.configSetId(),
        subject,
        traceId));

    if (log.isInfoEnabled()) {
      log.info(
          "room_updated roomId={} subject={} traceId={} oldName={} newName={} oldConfigSetId={} newConfigSetId={}",
          room.roomId(),
          subject,
          traceId,
          oldRoom.name(),
          room.name(),
          oldRoom.configSetId(),
          room.configSetId());
    }

    return toResponse(room);
  }

  @PostMapping("/{roomId}/close")
  RoomResponse closeRoom(
      @PathVariable("roomId") String roomId,
      @AuthenticationPrincipal OAuth2User principal,
      HttpServletRequest httpRequest) {

    String traceId = problemResponseFacade.resolveTraceId(httpRequest);
    String subject = principal.getName();

    // Get old room for audit logging (AC6: old/new values)
    Room oldRoom = roomService.getRoom(roomId);
    tenantAccessGuard.assertAccess(oldRoom.tenantId(), principal);

    if (log.isInfoEnabled()) {
      log.info(
          "room_close_started roomId={} subject={} traceId={}",
          roomId,
          subject,
          traceId);
    }

    Room room = closeRoomUseCase.execute(new CloseRoomCommand(oldRoom, subject, traceId));

    if (log.isInfoEnabled()) {
      log.info(
          "room_closed roomId={} subject={} traceId={} oldStatus={} newStatus={}",
          room.roomId(),
          subject,
          traceId,
          oldRoom.status(),
          room.status());
    }

    return toResponse(room);
  }

  @DeleteMapping("/{roomId}")
  ResponseEntity<Void> deleteRoom(
      @PathVariable("roomId") String roomId,
      @AuthenticationPrincipal OAuth2User principal,
      HttpServletRequest httpRequest) {

    String traceId = problemResponseFacade.resolveTraceId(httpRequest);
    String subject = principal.getName();

    Room oldRoom = roomService.getRoom(roomId);
    tenantAccessGuard.assertAccess(oldRoom.tenantId(), principal);

    if (log.isInfoEnabled()) {
      log.info(
          "room_delete_started roomId={} subject={} traceId={}",
          roomId,
          subject,
          traceId);
    }

    deleteRoomUseCase.execute(new DeleteRoomCommand(oldRoom, subject, traceId));

    if (log.isInfoEnabled()) {
      log.info(
          "room_deleted roomId={} subject={} traceId={} oldName={} oldTenantId={} oldConfigSetId={} oldStatus={}",
          oldRoom.roomId(),
          subject,
          traceId,
          oldRoom.name(),
          oldRoom.tenantId(),
          oldRoom.configSetId(),
          oldRoom.status());
    }

    return ResponseEntity.noContent().build();
  }

  private static RoomResponse toResponse(Room room) {
    return new RoomResponse(
        room.roomId(),
        room.name(),
        room.description(),
        room.tenantId(),
        room.configSetId(),
        room.status().name().toLowerCase(),
        room.createdAt(),
        room.updatedAt());
  }
}
