package com.acme.jitsi.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;

/**
 * Centralized tenant access guard for web layer authorization.
 * Checks that the authenticated principal's tenant claim matches the requested
 * tenant.
 * Single source of truth — use in all controllers instead of duplicating this
 * logic.
 */
@Component
public class TenantAccessGuard {

  /**
   * Asserts that the principal has access to the given tenantId.
   *
   * @throws AccessDeniedException if the tenant claim is absent or does not match
   *                               {@code tenantId}
   */
  public void assertAccess(String tenantId, OAuth2User principal) {
    Object tenantIdClaim = resolveTenantClaim(principal);
    if (tenantIdClaim == null) {
      throw new AccessDeniedException("Tenant claim is required");
    }

    String actualTenantId;
    if (tenantIdClaim instanceof java.util.Collection<?> coll) {
      actualTenantId = coll.isEmpty() ? "" : coll.iterator().next().toString();
    } else {
      actualTenantId = tenantIdClaim.toString();
    }

    if (!actualTenantId.equals(tenantId)) {
      throw new AccessDeniedException("Requested tenant is not accessible for current principal");
    }
  }

  private Object resolveTenantClaim(OAuth2User principal) {
    Object tenantIdClaim = principal.getAttribute("tenantId");
    if (tenantIdClaim != null) {
      return tenantIdClaim;
    }
    return principal.getAttribute("tenant_id");
  }
}
