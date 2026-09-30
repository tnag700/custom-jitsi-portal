package com.acme.jitsi.domains.meetings.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.acme.jitsi.domains.meetings.event.MeetingCreatedEvent;
import com.acme.jitsi.domains.meetings.service.Meeting;
import com.acme.jitsi.domains.meetings.service.MeetingConfigSetInvalidException;
import com.acme.jitsi.domains.meetings.service.MeetingRepository;
import com.acme.jitsi.domains.meetings.service.MeetingRoomNotFoundException;
import com.acme.jitsi.domains.meetings.usecase.CreateMeetingCommand;
import com.acme.jitsi.domains.meetings.usecase.CreateMeetingUseCase;
import com.acme.jitsi.domains.rooms.service.ConfigSetValidator;
import com.acme.jitsi.domains.rooms.service.Room;
import com.acme.jitsi.domains.rooms.service.RoomNotFoundException;
import com.acme.jitsi.domains.rooms.service.RoomService;
import com.acme.jitsi.domains.rooms.service.RoomStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class RoomServiceMeetingRoomsAdapterTest {

  private final RoomService roomService = mock(RoomService.class);
  private final ConfigSetValidator validator = mock(ConfigSetValidator.class);
  private final RoomServiceMeetingRoomsAdapter adapter = new RoomServiceMeetingRoomsAdapter(roomService, validator);

  @ParameterizedTest
  @CsvSource({"false, ACTIVE", "true, ACTIVE", "false, CLOSED", "true, CLOSED"})
  void roomReadsDoNotDependOnConfigurationAvailability(boolean forUpdate, RoomStatus status) {
    Room room = new Room("room-1", "Room", null, "tenant-1", "config-1", status,
        Instant.EPOCH, Instant.EPOCH);
    when(forUpdate ? roomService.getRoomForUpdate("room-1") : roomService.getRoom("room-1"))
        .thenReturn(room);
    when(validator.isValid("config-1", "tenant-1"))
        .thenThrow(new IllegalStateException("Configuration storage unavailable"));

    var snapshot = forUpdate ? adapter.getRequiredRoomForUpdate("room-1") : adapter.getRequiredRoom("room-1");

    assertThat(snapshot.roomId()).isEqualTo("room-1");
    assertThat(snapshot.tenantId()).isEqualTo("tenant-1");
    assertThat(snapshot.configSetId()).isEqualTo("config-1");
    assertThat(snapshot.active()).isEqualTo(status == RoomStatus.ACTIVE);
    verifyNoInteractions(validator);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void missingRoomsRetainNotFoundCause(boolean forUpdate) {
    RoomNotFoundException failure = new RoomNotFoundException("room-missing");
    when(forUpdate ? roomService.getRoomForUpdate("room-missing") : roomService.getRoom("room-missing"))
        .thenThrow(failure);

    assertThatThrownBy(() -> {
      if (forUpdate) {
        adapter.getRequiredRoomForUpdate("room-missing");
      } else {
        adapter.getRequiredRoom("room-missing");
      }
    }).isInstanceOf(MeetingRoomNotFoundException.class).hasCause(failure);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void creationChecksConfigurationOnceAfterRoomLockInsideTransaction(boolean configValid) {
    Room room = new Room("room-1", "Room", null, "tenant-1", "config-1", RoomStatus.ACTIVE,
        Instant.EPOCH, Instant.EPOCH);
    when(roomService.getRoom("room-1")).thenReturn(room);
    when(roomService.getRoomForUpdate("room-1")).thenAnswer(invocation -> {
      assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
      return room;
    });
    when(validator.isValid("config-1", "tenant-1")).thenAnswer(invocation -> {
      assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
      return configValid;
    });
    MeetingRepository repository = mock(MeetingRepository.class);
    ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    CreateMeetingUseCase target = new CreateMeetingUseCase(repository, adapter, events,
        Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
    TransactionInterceptor transactions = new TransactionInterceptor();
    transactions.setTransactionManager(new DataSourceTransactionManager(
        new DriverManagerDataSource("jdbc:h2:mem:room-config-check")));
    transactions.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
    ProxyFactory factory = new ProxyFactory(target);
    factory.setProxyTargetClass(true);
    factory.addAdvice(transactions);
    CreateMeetingUseCase useCase = (CreateMeetingUseCase) factory.getProxy();
    CreateMeetingCommand command = new CreateMeetingCommand(
        "room-1", "Title", "Desc", "scheduled", Instant.EPOCH, Instant.EPOCH.plusSeconds(3600),
        true, false, "actor-1", "trace-1");

    assertThat(adapter.getRequiredRoom("room-1").tenantId()).isEqualTo("tenant-1");
    if (configValid) {
      when(repository.save(any(Meeting.class))).thenAnswer(invocation -> invocation.getArgument(0));
      assertThat(useCase.execute(command).configSetId()).isEqualTo("config-1");
    } else {
      assertThatThrownBy(() -> useCase.execute(command)).isInstanceOf(MeetingConfigSetInvalidException.class);
      verifyNoInteractions(repository, events);
    }

    var order = inOrder(roomService, validator, repository, events);
    order.verify(roomService).getRoom("room-1");
    order.verify(roomService).getRoomForUpdate("room-1");
    order.verify(validator).isValid("config-1", "tenant-1");
    if (configValid) {
      order.verify(repository).save(any(Meeting.class));
      order.verify(events).publishEvent(any(MeetingCreatedEvent.class));
    }
    order.verifyNoMoreInteractions();
  }
}
