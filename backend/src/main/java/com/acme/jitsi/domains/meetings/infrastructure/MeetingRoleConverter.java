package com.acme.jitsi.domains.meetings.infrastructure;

import com.acme.jitsi.domains.meetings.service.MeetingRole;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
class MeetingRoleConverter implements AttributeConverter<MeetingRole, String> {

  @Override
  public String convertToDatabaseColumn(MeetingRole attribute) {
    if (attribute == null) {
      return null;
    }
    return attribute.value;
  }

  @Override
  public MeetingRole convertToEntityAttribute(String dbData) {
    if (dbData == null) {
      return null;
    }
    return MeetingRole.from(dbData)
        .orElseThrow(() -> new IllegalArgumentException("Unknown MeetingRole value: " + dbData));
  }
}
