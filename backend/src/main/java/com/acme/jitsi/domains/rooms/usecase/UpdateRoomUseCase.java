package com.acme.jitsi.domains.rooms.usecase;

import com.acme.jitsi.domains.rooms.event.RoomUpdatedEvent;
import com.acme.jitsi.domains.rooms.service.ConfigSetInvalidException;
import com.acme.jitsi.domains.rooms.service.ConfigSetValidator;
import com.acme.jitsi.domains.rooms.service.InvalidRoomDataException;
import com.acme.jitsi.domains.rooms.service.Room;
import com.acme.jitsi.domains.rooms.service.RoomNameConflictException;
import com.acme.jitsi.domains.rooms.service.RoomRepository;
import com.acme.jitsi.infrastructure.usecase.UseCase;
import java.time.Clock;
import java.time.Instant;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UpdateRoomUseCase implements UseCase<UpdateRoomCommand, Room> {

  private final RoomRepository roomRepository;
  private final ConfigSetValidator configSetValidator;
  private final ApplicationEventPublisher eventPublisher;
  private final Clock clock;

  public UpdateRoomUseCase(
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
  public Room execute(UpdateRoomCommand command) {
    Room existing = command.existing();

    String normalizedName = normalizeName(command.name(), existing.name());
    String normalizedDescription = normalizeDescription(command.description(), existing.description());

    if (isNameChanged(normalizedName, existing.name())) {
      validateNameUniqueness(normalizedName, existing.tenantId(), existing.roomId());
    }

    if (isConfigSetChanged(command.configSetId(), existing.configSetId())) {
      validateConfigSet(command.configSetId());
    }

    Instant now = Instant.now(clock);
    Room updated = new Room(
        existing.roomId(),
        normalizedName,
        normalizedDescription,
        existing.tenantId(),
        command.configSetId() != null ? command.configSetId() : existing.configSetId(),
        existing.status(),
        existing.createdAt(),
        now);

    Room saved = roomRepository.save(updated);

    eventPublisher.publishEvent(new RoomUpdatedEvent(
        saved.roomId(),
        command.actorId(),
        command.traceId(),
        "name,description,configSetId",
        "name=%s,description=%s,configSetId=%s"
            .formatted(existing.name(), existing.description(), existing.configSetId()),
        "name=%s,description=%s,configSetId=%s"
            .formatted(saved.name(), saved.description(), saved.configSetId())));

    return saved;
  }

  private String normalizeName(String requestedName, String existingName) {
    if (requestedName == null) {
      return existingName;
    }

    String normalizedName = requestedName.trim();
    if (normalizedName.isEmpty()) {
      throw new InvalidRoomDataException("Room name must not be blank");
    }
    return normalizedName;
  }

  private String normalizeDescription(String requestedDescription, String existingDescription) {
    if (requestedDescription == null) {
      return existingDescription;
    }
    return requestedDescription.isBlank() ? null : requestedDescription.trim();
  }

  private boolean isNameChanged(String normalizedName, String existingName) {
    return !normalizedName.equals(existingName);
  }

  private boolean isConfigSetChanged(String requestedConfigSetId, String existingConfigSetId) {
    return requestedConfigSetId != null && !requestedConfigSetId.equals(existingConfigSetId);
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
