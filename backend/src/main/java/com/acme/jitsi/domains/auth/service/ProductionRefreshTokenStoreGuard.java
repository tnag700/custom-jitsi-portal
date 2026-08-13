package com.acme.jitsi.domains.auth.service;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("prod")
class ProductionRefreshTokenStoreGuard implements InitializingBean {

  private final AuthRefreshProperties properties;

  ProductionRefreshTokenStoreGuard(AuthRefreshProperties properties) {
    this.properties = properties;
  }

  @Override
  public void afterPropertiesSet() {
    String configuredMode = properties.atomicStore();
    if (configuredMode == null || !"database".equalsIgnoreCase(configuredMode.trim())) {
      throw new IllegalStateException(
          "Production refresh-token replay protection requires app.auth.refresh.atomic-store=database.");
    }
    if (properties.acceptIssuedAfter() == null) {
      throw new IllegalStateException(
          "Production refresh-token replay protection requires app.auth.refresh.accept-issued-after.");
    }
  }
}
