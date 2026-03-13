package com.acme.jitsi.domains.meetings.service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

final class MeetingJoinPreparationHelper {

  private final MeetingService meetingService;
  private final MeetingProfilesPort meetingProfilesPort;
  private final MeetingTokenProperties properties;

  MeetingJoinPreparationHelper(
      MeetingService meetingService,
      MeetingProfilesPort meetingProfilesPort,
      MeetingTokenProperties properties) {
    this.meetingService = meetingService;
    this.meetingProfilesPort = meetingProfilesPort;
    this.properties = properties;
  }

  String resolveDisplayName(String subject) {
    try {
      MeetingProfileSnapshot profile = meetingProfilesPort.findBySubjectId(subject);
      if (profile == null) {
        return null;
      }

      String fullName = normalizeDisplayPart(profile.fullName());
      if (!fullName.isBlank()) {
        return fullName;
      }

      String organization = normalizeDisplayPart(profile.organization());
      return organization.isBlank() ? null : organization;
    } catch (RuntimeException ignored) {
      return null;
    }
  }

  String buildJoinUrl(String meetingId, String accessToken, String displayName) {
    String roomSegment = resolveRoomPathSegment(meetingId);
    String encodedJwt = encodeFragmentValue("\"" + accessToken + "\"");
    String joinUrl = properties.joinUrlTemplate().formatted(roomSegment, encodedJwt);

    if (displayName == null || displayName.isBlank()) {
      return joinUrl;
    }

    String encodedDisplayName = encodeFragmentValue("\"" + displayName + "\"");
    return joinUrl
        + "&userInfo.displayName="
        + encodedDisplayName
        + "&config.defaultLocalDisplayName="
        + encodedDisplayName;
  }

  String resolveRoomClaim(String meetingId) {
    return resolveRoomPathSegment(meetingId).toLowerCase(Locale.ROOT);
  }

  private String resolveRoomPathSegment(String meetingId) {
    return encodePathSegment(resolveRawRoomName(meetingId));
  }

  private String resolveRawRoomName(String meetingId) {
    try {
      Meeting meeting = meetingService.getMeeting(meetingId);
      if (meeting == null || meeting.title() == null || meeting.title().isBlank()) {
        return meetingId;
      }

      String normalized =
          meeting.title().trim()
              .toLowerCase(Locale.ROOT)
              .replaceAll("\\s+", "-")
              .replaceAll("[^\\p{L}\\p{N}_-]", "-")
              .replaceAll("-+", "-")
              .replaceAll("^-|-$", "");

      return normalized.isBlank() ? meetingId : normalized;
    } catch (RuntimeException ignored) {
      return meetingId;
    }
  }

  private String encodeFragmentValue(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
  }

  private String encodePathSegment(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8)
        .replace("+", "%20")
        .replace("%2D", "-")
        .replace("%5F", "_");
  }

  private String normalizeDisplayPart(String value) {
    return value == null ? "" : value.trim();
  }
}