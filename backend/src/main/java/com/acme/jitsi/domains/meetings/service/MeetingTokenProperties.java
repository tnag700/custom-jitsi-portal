package com.acme.jitsi.domains.meetings.service;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties("app.meetings.token")
@Validated
class MeetingTokenProperties {

  enum UnknownRolePolicy {
    FALLBACK_PARTICIPANT("fallback-participant"),
    DENY_ACCESS("deny-access");

    private final String value;

    UnknownRolePolicy(String value) {
      this.value = value;
    }

    static UnknownRolePolicy from(String value) {
      if (value == null || value.isBlank()) {
        return FALLBACK_PARTICIPANT;
      }

      String normalized = value.trim().toLowerCase(java.util.Locale.ROOT);
      for (UnknownRolePolicy policy : values()) {
        if (policy.value.equals(normalized)) {
          return policy;
        }
      }

      throw new IllegalArgumentException("app.meetings.token.unknown-role-policy must be fallback-participant or deny-access");
    }
  }

  private String issuer = "jitsi-portal";
  private String audience = "jitsi-meet";
  private String algorithm = "HS256";
  @Min(15)
  @Max(30)
  private int ttlMinutes = 20;
  private String signingSecret = "";
  private String roleClaimName = "role";
  private UnknownRolePolicy unknownRolePolicy = UnknownRolePolicy.DENY_ACCESS;
  private String joinUrlTemplate = "https://meet.example/%s#jwt=%s";
  private List<String> knownMeetingIds = new ArrayList<>();
  private Set<String> blockedSubjects = new HashSet<>();
  private List<RoleAssignment> assignments = new ArrayList<>();
  private List<UpcomingMeetingDefinition> upcomingMeetings = new ArrayList<>();

  String issuer() {
    return issuer;
  }

  public void setIssuer(String issuer) {
    this.issuer = issuer;
  }

  String audience() {
    return audience;
  }

  public void setAudience(String audience) {
    this.audience = audience;
  }

  String algorithm() {
    return algorithm;
  }

  public void setAlgorithm(String algorithm) {
    this.algorithm = algorithm;
  }

  int ttlMinutes() {
    return ttlMinutes;
  }

  public void setTtlMinutes(int ttlMinutes) {
    this.ttlMinutes = ttlMinutes;
  }

  String signingSecret() {
    return signingSecret;
  }

  public void setSigningSecret(String signingSecret) {
    this.signingSecret = signingSecret;
  }

  String roleClaimName() {
    return roleClaimName;
  }

  public void setRoleClaimName(String roleClaimName) {
    this.roleClaimName = roleClaimName;
  }

  String joinUrlTemplate() {
    return joinUrlTemplate;
  }

  UnknownRolePolicy unknownRolePolicy() {
    return unknownRolePolicy;
  }

  public void setUnknownRolePolicy(String unknownRolePolicy) {
    this.unknownRolePolicy = UnknownRolePolicy.from(unknownRolePolicy);
  }

  public void setJoinUrlTemplate(String joinUrlTemplate) {
    if (joinUrlTemplate == null || joinUrlTemplate.isBlank()) {
      throw new IllegalArgumentException("app.meetings.token.join-url-template must not be blank");
    }
    if (joinUrlTemplate.contains("?jwt=%s")) {
      throw new IllegalArgumentException("app.meetings.token.join-url-template must not place jwt in query");
    }
    if (!joinUrlTemplate.contains("#jwt=%s")) {
      throw new IllegalArgumentException("app.meetings.token.join-url-template must include jwt in URL fragment");
    }
    this.joinUrlTemplate = joinUrlTemplate;
  }

  List<String> knownMeetingIds() {
    return knownMeetingIds;
  }

  public void setKnownMeetingIds(List<String> knownMeetingIds) {
    this.knownMeetingIds = knownMeetingIds;
  }

  Set<String> blockedSubjects() {
    return blockedSubjects;
  }

  public void setBlockedSubjects(Set<String> blockedSubjects) {
    this.blockedSubjects = blockedSubjects;
  }

  List<RoleAssignment> assignments() {
    return assignments;
  }

  public void setAssignments(List<RoleAssignment> assignments) {
    this.assignments = assignments;
  }

  List<UpcomingMeetingDefinition> upcomingMeetings() {
    return upcomingMeetings;
  }

  public void setUpcomingMeetings(List<UpcomingMeetingDefinition> upcomingMeetings) {
    this.upcomingMeetings = upcomingMeetings;
  }

  static class RoleAssignment {
    private String meetingId;
    private String subject;
    private String role;

    String meetingId() {
      return meetingId;
    }

    public void setMeetingId(String meetingId) {
      this.meetingId = meetingId;
    }

    String subject() {
      return subject;
    }

    public void setSubject(String subject) {
      this.subject = subject;
    }

    String role() {
      return role;
    }

    public void setRole(String role) {
      this.role = role;
    }
  }

  static class UpcomingMeetingDefinition {
    private String meetingId;
    private String title;
    private Instant startsAt;
    private String roomName;

    String meetingId() {
      return meetingId;
    }

    public void setMeetingId(String meetingId) {
      this.meetingId = meetingId;
    }

    String title() {
      return title;
    }

    public void setTitle(String title) {
      this.title = title;
    }

    Instant startsAt() {
      return startsAt;
    }

    public void setStartsAt(Instant startsAt) {
      this.startsAt = startsAt;
    }

    String roomName() {
      return roomName;
    }

    public void setRoomName(String roomName) {
      this.roomName = roomName;
    }
  }
}
