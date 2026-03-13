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

  String getConfigSetId() {
    return configSetId;
  }

  void setConfigSetId(String configSetId) {
    this.configSetId = configSetId;
  }

  String getName() {
    return name;
  }

  void setName(String name) {
    this.name = name;
  }

  String getTenantId() {
    return tenantId;
  }

  void setTenantId(String tenantId) {
    this.tenantId = tenantId;
  }

  ConfigSetEnvironmentType getEnvironmentType() {
    return environmentType;
  }

  void setEnvironmentType(ConfigSetEnvironmentType environmentType) {
    this.environmentType = environmentType;
  }

  String getIssuer() {
    return issuer;
  }

  void setIssuer(String issuer) {
    this.issuer = issuer;
  }

  String getAudience() {
    return audience;
  }

  void setAudience(String audience) {
    this.audience = audience;
  }

  String getAlgorithm() {
    return algorithm;
  }

  void setAlgorithm(String algorithm) {
    this.algorithm = algorithm;
  }

  String getRoleClaim() {
    return roleClaim;
  }

  void setRoleClaim(String roleClaim) {
    this.roleClaim = roleClaim;
  }

  String getSigningSecretEncrypted() {
    return signingSecretEncrypted;
  }

  void setSigningSecretEncrypted(String signingSecretEncrypted) {
    this.signingSecretEncrypted = signingSecretEncrypted;
  }

  String getJwksUri() {
    return jwksUri;
  }

  void setJwksUri(String jwksUri) {
    this.jwksUri = jwksUri;
  }

  int getAccessTtlMinutes() {
    return accessTtlMinutes;
  }

  void setAccessTtlMinutes(int accessTtlMinutes) {
    this.accessTtlMinutes = accessTtlMinutes;
  }

  Integer getRefreshTtlMinutes() {
    return refreshTtlMinutes;
  }

  void setRefreshTtlMinutes(Integer refreshTtlMinutes) {
    this.refreshTtlMinutes = refreshTtlMinutes;
  }

  String getMeetingsServiceUrl() {
    return meetingsServiceUrl;
  }

  void setMeetingsServiceUrl(String meetingsServiceUrl) {
    this.meetingsServiceUrl = meetingsServiceUrl;
  }

  ConfigSetStatus getStatus() {
    return status;
  }

  void setStatus(ConfigSetStatus status) {
    this.status = status;
  }

  Instant getCreatedAt() {
    return createdAt;
  }

  void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  Instant getUpdatedAt() {
    return updatedAt;
  }

  void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }

  boolean isDeleted() {
    return deleted;
  }

  void setDeleted(boolean deleted) {
    this.deleted = deleted;
  }
}