package com.acme.jitsi.domains.rooms.usecase;

import com.acme.jitsi.domains.rooms.event.RoomCreatedEvent;
import com.acme.jitsi.domains.rooms.service.ConfigSetInvalidException;
import com.acme.jitsi.domains.rooms.service.ConfigSetValidator;
import com.acme.jitsi.domains.rooms.service.InvalidRoomDataException;
import com.acme.jitsi.domains.rooms.service.Room;
import com.acme.jitsi.domains.rooms.service.RoomNameConflictException;
import com.acme.jitsi.domains.rooms.service.RoomRepository;
import com.acme.jitsi.domains.rooms.service.RoomStatus;
import com.acme.jitsi.infrastructure.usecase.UseCase;
import com.acme.jitsi.shared.validation.TextInputNormalizer;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreateRoomUseCase implements UseCase<CreateRoomCommand, Room> {

  private final RoomRepository roomRepository;
  private final ConfigSetValidator configSetValidator;
  private final ApplicationEventPublisher eventPublisher;
  private final Clock clock;

  public CreateRoomUseCase(
      RoomRepository roomRepository,
      ConfigSetValidator configSetValidator,
      ApplicationEventPublisher eventPublisher,
      Clock clock) {
    this.roomRepository = roomRepository;
    this.configSetValidator = configSetValidator;
    this.eventPublisher = eventPublisher;
    this.clock = clock;
  }

  @Override
  @Transactional
  public Room execute(CreateRoomCommand command) {
    String normalizedName = TextInputNormalizer.normalizeRequired(command.name());
    String normalizedDescription = TextInputNormalizer.normalizeOptional(command.description());
    String normalizedTenantId = TextInputNormalizer.normalizeRequired(command.tenantId());
    String normalizedConfigSetId = TextInputNormalizer.normalizeRequired(command.configSetId());

    validateRequiredFields(normalizedName, normalizedTenantId, normalizedConfigSetId);
    validateConfigSet(normalizedConfigSetId);
    validateNameUniqueness(normalizedName, normalizedTenantId, null);

    Instant now = Instant.now(clock);
    Room room = new Room(
        UUID.randomUUID().toString(),
      normalizedName,
      normalizedDescription,
      normalizedTenantId,
      normalizedConfigSetId,
        RoomStatus.ACTIVE,
        now,
        now);

    Room saved = roomRepository.save(room);

    eventPublisher.publishEvent(new RoomCreatedEvent(
        saved.roomId(),
        command.actorId(),
        command.traceId(),
        "name,description,tenantId,configSetId,status",
        "-",
        "name=%s,description=%s,tenantId=%s,configSetId=%s,status=%s"
            .formatted(saved.name(), saved.description(), saved.tenantId(), saved.configSetId(), saved.status())));

    return saved;
  }

  private void validateRequiredFields(String name, String tenantId, String configSetId) {
    if (isBlank(name)) {
      throw new InvalidRoomDataException("Room name is required");
    }
    if (isBlank(tenantId)) {
      throw new InvalidRoomDataException("Tenant ID is required");
    }
    if (isBlank(configSetId)) {
      throw new InvalidRoomDataException("Config set ID is required");
    }
  }

  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  private void validateConfigSet(String configSetId) {
    if (!configSetValidator.isValid(configSetId)) {
      throw new ConfigSetInvalidException(configSetId);
    }
  }

  private void validateNameUniqueness(String name, String tenantId, String excludeRoomId) {
    boolean exists;
    if (excludeRoomId != null) {
      exists = roomRepository.existsByNameAndTenantIdAndRoomIdNot(name, tenantId, excludeRoomId);
    } else {
      exists = roomRepository.existsByNameAndTenantId(name, tenantId);
    }
    if (exists) {
      throw new RoomNameConflictException(name, tenantId);
    }
  }
}
