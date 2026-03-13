package com.acme.jitsi.domains.configsets.infrastructure;

import com.acme.jitsi.domains.configsets.service.ConfigSet;
import org.springframework.stereotype.Component;

@Component
class ConfigSetPersistenceTranslator {

  private final ConfigSetEncryptionService encryptionService;

  ConfigSetPersistenceTranslator(ConfigSetEncryptionService encryptionService) {
    this.encryptionService = encryptionService;
  }

  ConfigSetEntity toNewEntity(ConfigSet configSet) {
    ConfigSetEntity entity = new ConfigSetEntity();
    entity.setConfigSetId(configSet.configSetId());
    copyState(entity, configSet);
    entity.setDeleted(false);
    return entity;
  }

  ConfigSet toDomain(ConfigSetEntity entity) {
    return new ConfigSet(
        entity.getConfigSetId(),
        entity.getName(),
        entity.getTenantId(),
        entity.getEnvironmentType(),
        entity.getIssuer(),
        entity.getAudience(),
        entity.getAlgorithm(),
        entity.getRoleClaim(),
        encryptionService.decrypt(entity.getSigningSecretEncrypted()),
        entity.getJwksUri(),
        entity.getAccessTtlMinutes(),
        entity.getRefreshTtlMinutes(),
        entity.getMeetingsServiceUrl(),
        entity.getStatus(),
        entity.getCreatedAt(),
        entity.getUpdatedAt());
  }

  private void copyState(ConfigSetEntity entity, ConfigSet configSet) {
    entity.setName(configSet.name());
    entity.setTenantId(configSet.tenantId());
    entity.setEnvironmentType(configSet.environmentType());
    entity.setIssuer(configSet.issuer());
    entity.setAudience(configSet.audience());
    entity.setAlgorithm(configSet.algorithm());
    entity.setRoleClaim(configSet.roleClaim());
    entity.setSigningSecretEncrypted(encryptionService.encrypt(configSet.signingSecret()));
    entity.setJwksUri(configSet.jwksUri());
    entity.setAccessTtlMinutes(configSet.accessTtlMinutes());
    entity.setRefreshTtlMinutes(configSet.refreshTtlMinutes());
    entity.setMeetingsServiceUrl(configSet.meetingsServiceUrl());
    entity.setStatus(configSet.status());
    entity.setCreatedAt(configSet.createdAt());
    entity.setUpdatedAt(configSet.updatedAt());
  }
}