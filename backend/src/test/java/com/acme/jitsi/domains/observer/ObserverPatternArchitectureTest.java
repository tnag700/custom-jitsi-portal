package com.acme.jitsi.domains.observer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.bind.annotation.RequestMapping;

class ObserverPatternArchitectureTest {

  @Test
  void listenersUseAsyncAfterCommitForEventHandling() throws Exception {
    assertListenerMethodIsAsyncAfterCommit(
        "com.acme.jitsi.domains.rooms.listener.RoomAuditListener",
        "handleRoomCreatedEvent",
        "com.acme.jitsi.domains.rooms.event.RoomCreatedEvent");
    assertListenerMethodIsAsyncAfterCommit(
        "com.acme.jitsi.domains.rooms.listener.RoomAuditListener",
        "handleRoomUpdatedEvent",
        "com.acme.jitsi.domains.rooms.event.RoomUpdatedEvent");
    assertListenerMethodIsAsyncAfterCommit(
        "com.acme.jitsi.domains.rooms.listener.RoomAuditListener",
        "handleRoomClosedEvent",
        "com.acme.jitsi.domains.rooms.event.RoomClosedEvent");
    assertListenerMethodIsAsyncAfterCommit(
        "com.acme.jitsi.domains.rooms.listener.RoomAuditListener",
        "handleRoomDeletedEvent",
        "com.acme.jitsi.domains.rooms.event.RoomDeletedEvent");

    assertListenerMethodIsAsyncAfterCommit(
        "com.acme.jitsi.domains.meetings.listener.MeetingAuditListener",
        "handleMeetingCreatedEvent",
        "com.acme.jitsi.domains.meetings.event.MeetingCreatedEvent");
    assertListenerMethodIsAsyncAfterCommit(
        "com.acme.jitsi.domains.meetings.listener.MeetingAuditListener",
        "handleMeetingUpdatedEvent",
        "com.acme.jitsi.domains.meetings.event.MeetingUpdatedEvent");
    assertListenerMethodIsAsyncAfterCommit(
        "com.acme.jitsi.domains.meetings.listener.MeetingAuditListener",
        "handleMeetingCanceledEvent",
        "com.acme.jitsi.domains.meetings.event.MeetingCanceledEvent");
    assertListenerMethodIsAsyncAfterCommit(
        "com.acme.jitsi.domains.meetings.listener.MeetingInviteAuditListener",
        "onInviteCreated",
        "com.acme.jitsi.domains.meetings.event.MeetingInviteCreatedEvent");
    assertListenerMethodIsAsyncAfterCommit(
        "com.acme.jitsi.domains.meetings.listener.MeetingInviteAuditListener",
        "onBulkInviteCreated",
        "com.acme.jitsi.domains.meetings.event.MeetingBulkInviteCreatedEvent");
    assertListenerMethodIsAsyncAfterCommit(
        "com.acme.jitsi.domains.meetings.listener.MeetingInviteAuditListener",
        "onInviteRevoked",
        "com.acme.jitsi.domains.meetings.event.MeetingInviteRevokedEvent");

    assertListenerMethodIsAsyncAfterCommit(
        "com.acme.jitsi.domains.meetings.listener.MeetingParticipantAuditListener",
        "onParticipantAssigned",
        "com.acme.jitsi.domains.meetings.event.MeetingParticipantAssignedEvent");
    assertListenerMethodIsAsyncAfterCommit(
        "com.acme.jitsi.domains.meetings.listener.MeetingParticipantAuditListener",
        "onParticipantRoleChanged",
        "com.acme.jitsi.domains.meetings.event.MeetingParticipantRoleChangedEvent");
    assertListenerMethodIsAsyncAfterCommit(
        "com.acme.jitsi.domains.meetings.listener.MeetingParticipantAuditListener",
        "onParticipantRemoved",
        "com.acme.jitsi.domains.meetings.event.MeetingParticipantRemovedEvent");

    assertListenerMethodIsAsyncAfterCommit(
        "com.acme.jitsi.domains.configsets.infrastructure.ConfigSetAuditListener",
        "onCreated",
        "com.acme.jitsi.domains.configsets.event.ConfigSetCreatedEvent");
    assertListenerMethodIsAsyncAfterCommit(
        "com.acme.jitsi.domains.configsets.infrastructure.ConfigSetAuditListener",
        "onUpdated",
        "com.acme.jitsi.domains.configsets.event.ConfigSetUpdatedEvent");
    assertListenerMethodIsAsyncAfterCommit(
        "com.acme.jitsi.domains.configsets.infrastructure.ConfigSetAuditListener",
        "onActivated",
        "com.acme.jitsi.domains.configsets.event.ConfigSetActivatedEvent");
    assertListenerMethodIsAsyncAfterCommit(
        "com.acme.jitsi.domains.configsets.infrastructure.ConfigSetAuditListener",
        "onDeactivated",
        "com.acme.jitsi.domains.configsets.event.ConfigSetDeactivatedEvent");
    assertListenerMethodIsAsyncAfterCommit(
        "com.acme.jitsi.domains.configsets.infrastructure.ConfigSetAuditListener",
        "onRolloutCompleted",
        "com.acme.jitsi.domains.configsets.event.ConfigSetRolloutCompletedEvent");
    assertListenerMethodIsAsyncAfterCommit(
        "com.acme.jitsi.domains.configsets.infrastructure.ConfigSetAuditListener",
        "onRollbackCompleted",
        "com.acme.jitsi.domains.configsets.event.ConfigSetRollbackCompletedEvent");
  }

  @Test
  void businessLayerDoesNotInjectAuditOrMetricsServicesDirectly() throws Exception {
    List<String> businessClassNames = List.of(
        "com.acme.jitsi.domains.rooms.api.RoomsController",
        "com.acme.jitsi.domains.configsets.api.ConfigSetsController",
        "com.acme.jitsi.domains.rooms.service.RoomService",
        "com.acme.jitsi.domains.configsets.service.ConfigSetService",
        "com.acme.jitsi.domains.meetings.api.MeetingsController",
        "com.acme.jitsi.domains.meetings.service.MeetingInviteService",
        "com.acme.jitsi.domains.meetings.service.MeetingParticipantAssignmentService",
        "com.acme.jitsi.domains.meetings.service.MeetingService");

    List<String> forbiddenTypeNames = List.of(
        "com.acme.jitsi.domains.rooms.service.RoomAuditLog",
        "com.acme.jitsi.domains.configsets.service.ConfigSetAuditLog",
        "com.acme.jitsi.domains.meetings.service.MeetingAuditLog",
        "io.micrometer.core.instrument.MeterRegistry");

    for (String className : businessClassNames) {
      Class<?> clazz = Class.forName(className);
      for (java.lang.reflect.Constructor<?> ctor : clazz.getDeclaredConstructors()) {
        for (Class<?> paramType : ctor.getParameterTypes()) {
          for (String forbidden : forbiddenTypeNames) {
            assertFalse(
                paramType.getName().equals(forbidden),
                className + " must not inject " + forbidden + " via constructor");
          }
        }
      }
      for (java.lang.reflect.Field field : clazz.getDeclaredFields()) {
        for (String forbidden : forbiddenTypeNames) {
          assertFalse(
              field.getType().getName().equals(forbidden),
              className + " must not have field of type " + forbidden);
        }
      }
    }
  }

    @Test
    void controllersUseVersionedMappingsWithoutHardcodedApiV1Prefix() throws Exception {
        List<String> controllerClassNames = List.of(
                "com.acme.jitsi.domains.auth.api.AuthController",
                "com.acme.jitsi.domains.health.api.HealthController",
                "com.acme.jitsi.domains.invites.api.InviteExchangeController",
                "com.acme.jitsi.domains.rooms.api.RoomsController",
                "com.acme.jitsi.domains.configsets.api.ConfigSetsController",
                "com.acme.jitsi.domains.meetings.api.MeetingsController",
                "com.acme.jitsi.domains.meetings.api.UpcomingMeetingsController",
                "com.acme.jitsi.domains.meetings.api.MeetingInvitesController",
                "com.acme.jitsi.domains.meetings.api.MeetingParticipantAssignmentsController",
                "com.acme.jitsi.domains.meetings.api.MeetingAccessTokenController");

        for (String className : controllerClassNames) {
            Class<?> controllerClass = Class.forName(className);
            RequestMapping requestMapping = controllerClass.getAnnotation(RequestMapping.class);

            assertNotNull(requestMapping, className + " must declare @RequestMapping at class level");

            for (String mappedPath : requestMapping.value()) {
                assertFalse(
                        mappedPath.contains("/api/v1"),
                        className + " must not hardcode '/api/v1' in @RequestMapping");
            }

              String version = requestMapping.version();
              assertFalse(
                  version == null || version.isBlank(),
                  className + " must declare version attribute in @RequestMapping");
              assertEquals(
                  "v1",
                  version,
                  className + " must use version 'v1' in @RequestMapping");
        }
    }

  private static void assertListenerMethodIsAsyncAfterCommit(
      String listenerTypeName,
      String methodName,
      String eventTypeName) throws Exception {
    Class<?> listenerType = Class.forName(listenerTypeName);
    Class<?> eventType = Class.forName(eventTypeName);
    Method method = listenerType.getDeclaredMethod(methodName, eventType);

    assertTrue(method.isAnnotationPresent(Async.class),
        listenerTypeName + "#" + methodName + " must be annotated with @Async");

    TransactionalEventListener transactionalEventListener = method.getAnnotation(TransactionalEventListener.class);
    assertNotNull(transactionalEventListener,
        listenerTypeName + "#" + methodName + " must be annotated with @TransactionalEventListener");
    assertEquals(
        TransactionPhase.AFTER_COMMIT,
        transactionalEventListener.phase(),
        listenerTypeName + "#" + methodName + " must use AFTER_COMMIT phase");
    assertTrue(
        transactionalEventListener.fallbackExecution(),
        listenerTypeName + "#" + methodName + " must enable fallbackExecution");
  }
}
