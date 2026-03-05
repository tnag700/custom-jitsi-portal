package com.acme.jitsi.domains.configsets.infrastructure;

import com.acme.jitsi.domains.configsets.service.ConfigSet;
import com.acme.jitsi.domains.configsets.service.ConfigSetEnvironmentType;
import com.acme.jitsi.domains.configsets.service.ConfigSetStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "config_sets")
@SQLDelete(sql = "UPDATE config_sets SET deleted = true WHERE config_set_id = ?")
@SQLRestriction("deleted = false")
class ConfigSetEntity {

  @Id
  @Column(name = "config_set_id", nullable = false, updatable = false)
  private String configSetId;

  @Column(name = "name", nullable = false)
  private String name;

  @Column(name = "tenant_id", nullable = false)
  private String tenantId;

  @Enumerated(EnumType.STRING)
  @Column(name = "environment_type", nullable = false)
  private ConfigSetEnvironmentType environmentType;

  @Column(name = "issuer", nullable = false)
  private String issuer;

  @Column(name = "audience", nullable = false)
  private String audience;

  @Column(name = "algorithm", nullable = false)
  private String algorithm;

  @Column(name = "role_claim", nullable = false)
  private String roleClaim;

  @Column(name = "signing_secret_encrypted")
  private String signingSecretEncrypted;

  @Column(name = "jwks_uri")
  private String jwksUri;

  @Column(name = "access_ttl_minutes", nullable = false)
  private int accessTtlMinutes;

  @Column(name = "refresh_ttl_minutes")
  private Integer refreshTtlMinutes;

  @Column(name = "meetings_service_url", nullable = false)
  private String meetingsServiceUrl;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private ConfigSetStatus status;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Column(name = "deleted", nullable = false)
  private boolean deleted;

  protected ConfigSetEntity() {
  }

  ConfigSetEntity(ConfigSet configSet, ConfigSetEncryptionService encryptionService) {
    this.configSetId = configSet.configSetId();
    applyState(configSet, encryptionService, true);
    this.deleted = false;
  }

  void updateFrom(ConfigSet configSet, ConfigSetEncryptionService encryptionService) {
    applyState(configSet, encryptionService, false);
  }

  private void applyState(
      ConfigSet configSet,
      ConfigSetEncryptionService encryptionService,
      boolean includeCreatedAt) {
    this.name = configSet.name();
    this.tenantId = configSet.tenantId();
    this.environmentType = configSet.environmentType();
    this.issuer = configSet.issuer();
    this.audience = configSet.audience();
    this.algorithm = configSet.algorithm();
    this.roleClaim = configSet.roleClaim();
    this.signingSecretEncrypted = encryptionService.encrypt(configSet.signingSecret());
    this.jwksUri = configSet.jwksUri();
    this.accessTtlMinutes = configSet.accessTtlMinutes();
    this.refreshTtlMinutes = configSet.refreshTtlMinutes();
    this.meetingsServiceUrl = configSet.meetingsServiceUrl();
    this.status = configSet.status();

    if (includeCreatedAt) {
      this.createdAt = configSet.createdAt();
    }

    this.updatedAt = configSet.updatedAt();
  }

  ConfigSet toDomain(ConfigSetEncryptionService encryptionService) {
    return new ConfigSet(
        configSetId,
        name,
        tenantId,
        environmentType,
        issuer,
        audience,
        algorithm,
        roleClaim,
        encryptionService.decrypt(signingSecretEncrypted),
        jwksUri,
        accessTtlMinutes,
        refreshTtlMinutes,
        meetingsServiceUrl,
        status,
        createdAt,
        updatedAt);
  }

  String getConfigSetId() {
    return configSetId;
  }
}