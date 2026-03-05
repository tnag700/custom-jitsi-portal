package com.acme.jitsi.domains.profiles.infrastructure;

import com.acme.jitsi.domains.profiles.service.UserProfile;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "user_profiles")
class UserProfileEntity {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private String id;

  @Column(name = "subject_id", nullable = false, updatable = false, unique = true)
  private String subjectId;

  @Column(name = "tenant_id", nullable = false, updatable = false)
  private String tenantId;

  @Column(name = "full_name", nullable = false)
  private String fullName;

  @Column(name = "organization", nullable = false)
  private String organization;

  @Column(name = "position", nullable = false)
  private String position;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected UserProfileEntity() {
    // JPA
  }

  UserProfileEntity(UserProfile profile) {
    this.id = profile.id();
    this.subjectId = profile.subjectId();
    this.tenantId = profile.tenantId();
    this.fullName = profile.fullName();
    this.organization = profile.organization();
    this.position = profile.position();
    this.createdAt = profile.createdAt();
    this.updatedAt = profile.updatedAt();
  }

  UserProfile toDomain() {
    return new UserProfile(id, subjectId, tenantId, fullName, organization, position, createdAt, updatedAt);
  }

  void updateFrom(UserProfile profile) {
    this.fullName = profile.fullName();
    this.organization = profile.organization();
    this.position = profile.position();
    this.updatedAt = profile.updatedAt();
  }

  String getSubjectId() {
    return subjectId;
  }
}
