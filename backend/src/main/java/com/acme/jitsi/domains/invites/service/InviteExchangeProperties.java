package com.acme.jitsi.domains.invites.service;

import jakarta.validation.constraints.Min;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties("app.invites.exchange")
@Validated
class InviteExchangeProperties {

  private String atomicStore = "redis";
  private List<Invite> invites = new ArrayList<>();
  private Set<String> knownMeetingIds = new HashSet<>();
  private Set<String> closedMeetingIds = new HashSet<>();
  private Set<String> canceledMeetingIds = new HashSet<>();

  String atomicStore() {
    return atomicStore;
  }

  public void setAtomicStore(String atomicStore) {
    this.atomicStore = atomicStore;
  }

  List<Invite> invites() {
    return invites;
  }

  public void setInvites(List<Invite> invites) {
    this.invites = invites;
  }

  Set<String> knownMeetingIds() {
    return knownMeetingIds;
  }

  public void setKnownMeetingIds(Set<String> knownMeetingIds) {
    this.knownMeetingIds = knownMeetingIds;
  }

  Set<String> closedMeetingIds() {
    return closedMeetingIds;
  }

  public void setClosedMeetingIds(Set<String> closedMeetingIds) {
    this.closedMeetingIds = closedMeetingIds;
  }

  Set<String> canceledMeetingIds() {
    return canceledMeetingIds;
  }

  public void setCanceledMeetingIds(Set<String> canceledMeetingIds) {
    this.canceledMeetingIds = canceledMeetingIds;
  }

  static class Invite {
    private String token;
    private String meetingId;
    private Instant expiresAt;
    @Min(1)
    private int usageLimit = 1;
    private boolean revoked;

    String token() {
      return token;
    }

    public void setToken(String token) {
      this.token = token;
    }

    String meetingId() {
      return meetingId;
    }

    public void setMeetingId(String meetingId) {
      this.meetingId = meetingId;
    }

    Instant expiresAt() {
      return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
      this.expiresAt = expiresAt;
    }

    int usageLimit() {
      return usageLimit;
    }

    public void setUsageLimit(int usageLimit) {
      this.usageLimit = usageLimit;
    }

    boolean revoked() {
      return revoked;
    }

    public void setRevoked(boolean revoked) {
      this.revoked = revoked;
    }
  }
}