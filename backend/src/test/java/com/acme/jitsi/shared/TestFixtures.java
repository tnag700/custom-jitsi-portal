package com.acme.jitsi.shared;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;

import com.acme.jitsi.domains.meetings.service.Meeting;
import com.acme.jitsi.domains.meetings.service.MeetingStatus;
import com.acme.jitsi.domains.rooms.service.Room;
import com.acme.jitsi.domains.rooms.service.RoomStatus;
import java.time.Instant;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.OAuth2LoginRequestPostProcessor;

public final class TestFixtures {

  private static final Instant DEFAULT_CREATED_AT = Instant.parse("2026-01-01T00:00:00Z");
  private static final Instant DEFAULT_UPDATED_AT = Instant.parse("2026-01-01T00:05:00Z");
  private static final Instant DEFAULT_STARTS_AT = Instant.parse("2026-02-17T10:00:00Z");
  private static final Instant DEFAULT_ENDS_AT = Instant.parse("2026-02-17T11:00:00Z");

  private TestFixtures() {
  }

  public static OAuth2LoginRequestPostProcessor adminLogin() {
    return oauth2Login()
        .attributes(attrs -> attrs.put("sub", "admin-user"))
        .authorities(new SimpleGrantedAuthority("ROLE_admin"));
  }

  public static OAuth2LoginRequestPostProcessor adminLogin(String tenantId) {
    return oauth2Login()
        .attributes(attrs -> {
          attrs.put("sub", "admin-user");
          attrs.put("tenantId", tenantId);
          attrs.put("tenant_id", tenantId);
        })
        .authorities(new SimpleGrantedAuthority("ROLE_admin"));
  }

  public static Room room() {
    return room("room-1", "Room", null, "tenant-1", "config-1", RoomStatus.ACTIVE);
  }

  public static Room room(String roomId, String name, String tenantId, String configSetId, RoomStatus status) {
    return room(roomId, name, null, tenantId, configSetId, status);
  }

  public static Room room(
      String roomId,
      String name,
      String description,
      String tenantId,
      String configSetId,
      RoomStatus status) {
    return new Room(roomId, name, description, tenantId, configSetId, status, DEFAULT_CREATED_AT, DEFAULT_UPDATED_AT);
  }

  public static Meeting meeting(String meetingId, String roomId) {
    return Meeting.builder()
        .meetingId(meetingId)
        .roomId(roomId)
        .title("Meeting")
        .description("Description")
        .meetingType("scheduled")
        .configSetId("config-1")
        .status(MeetingStatus.SCHEDULED)
        .startsAt(DEFAULT_STARTS_AT)
        .endsAt(DEFAULT_ENDS_AT)
        .allowGuests(true)
        .recordingEnabled(false)
        .createdAt(DEFAULT_CREATED_AT)
        .updatedAt(DEFAULT_UPDATED_AT)
        .build();
  }

  public static Meeting mockMeeting(String meetingId, String roomId) {
    return meeting(meetingId, roomId);
  }
}