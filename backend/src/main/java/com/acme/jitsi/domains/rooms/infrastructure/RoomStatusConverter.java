package com.acme.jitsi.domains.rooms.infrastructure;

import com.acme.jitsi.domains.rooms.service.RoomStatus;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class RoomStatusConverter implements AttributeConverter<RoomStatus, String> {

  @Override
  public String convertToDatabaseColumn(RoomStatus attribute) {
    if (attribute == null) {
      return null;
    }
    return attribute.name().toLowerCase();
  }

  @Override
  public RoomStatus convertToEntityAttribute(String dbData) {
    if (dbData == null || dbData.isBlank()) {
      return null;
    }
    return RoomStatus.valueOf(dbData.trim().toUpperCase());
  }
}
