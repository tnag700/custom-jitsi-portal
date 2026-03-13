package com.acme.jitsi.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.core.user.OAuth2User;

class TenantAccessGuardTest {

  private final TenantAccessGuard guard = new TenantAccessGuard();

  @Test
  void allowsAccessWhenTenantIdClaimMatches() {
    OAuth2User principal = principalWithClaims("tenant-1", null);

    assertThatCode(() -> guard.assertAccess("tenant-1", principal)).doesNotThrowAnyException();
  }

  @Test
  void deniesAccessWhenTenantIdClaimDoesNotMatch() {
    OAuth2User principal = principalWithClaims("tenant-2", null);

    assertThatThrownBy(() -> guard.assertAccess("tenant-1", principal))
        .isInstanceOf(AccessDeniedException.class)
        .hasMessage("Requested tenant is not accessible for current principal");
  }

  @Test
  void allowsAccessWhenFallbackTenantIdClaimMatches() {
    OAuth2User principal = principalWithClaims(null, "tenant-1");

    assertThatCode(() -> guard.assertAccess("tenant-1", principal)).doesNotThrowAnyException();
  }

  @Test
  void deniesAccessWhenFallbackTenantIdClaimDoesNotMatch() {
    OAuth2User principal = principalWithClaims(null, "tenant-2");

    assertThatThrownBy(() -> guard.assertAccess("tenant-1", principal))
        .isInstanceOf(AccessDeniedException.class)
        .hasMessage("Requested tenant is not accessible for current principal");
  }

  @Test
  void collectionTenantClaimAllowsAccessWhenFirstElementMatches() {
    OAuth2User principal = Mockito.mock(OAuth2User.class);
    when(principal.getAttribute("tenantId")).thenReturn(List.of("tenant-1", "tenant-2"));

    assertThatCode(() -> guard.assertAccess("tenant-1", principal)).doesNotThrowAnyException();
  }

  @Test
  void collectionTenantClaimDeniesAccessWhenFirstElementDoesNotMatch() {
    OAuth2User principal = Mockito.mock(OAuth2User.class);
    when(principal.getAttribute("tenantId")).thenReturn(List.of("tenant-2", "tenant-1"));

    assertThatThrownBy(() -> guard.assertAccess("tenant-1", principal))
        .isInstanceOf(AccessDeniedException.class)
        .hasMessage("Requested tenant is not accessible for current principal");
  }

  @Test
  void emptyTenantCollectionClaimIsDenied() {
    OAuth2User principal = Mockito.mock(OAuth2User.class);
    when(principal.getAttribute("tenantId")).thenReturn(List.of());

    assertThatThrownBy(() -> guard.assertAccess("tenant-1", principal))
        .isInstanceOf(AccessDeniedException.class)
        .hasMessage("Tenant claim is required");
  }

  @Test
  void blankTenantClaimIsDenied() {
    OAuth2User principal = principalWithClaims("   ", null);

    assertThatThrownBy(() -> guard.assertAccess("tenant-1", principal))
        .isInstanceOf(AccessDeniedException.class)
        .hasMessage("Tenant claim is required");
  }

  @Test
  void collectionTenantClaimWithNullFirstElementIsDenied() {
    OAuth2User principal = Mockito.mock(OAuth2User.class);
    when(principal.getAttribute("tenantId")).thenReturn(java.util.Arrays.asList(null, "tenant-1"));

    assertThatThrownBy(() -> guard.assertAccess("tenant-1", principal))
        .isInstanceOf(AccessDeniedException.class)
        .hasMessage("Tenant claim is required");
  }

  @Test
  void collectionTenantClaimWithBlankFirstElementIsDenied() {
    OAuth2User principal = Mockito.mock(OAuth2User.class);
    when(principal.getAttribute("tenantId")).thenReturn(List.of("   "));

    assertThatThrownBy(() -> guard.assertAccess("tenant-1", principal))
        .isInstanceOf(AccessDeniedException.class)
        .hasMessage("Tenant claim is required");
  }

  @Test
  void fallbackTenantIdEmptyCollectionIsDenied() {
    OAuth2User principal = principalWithClaims(null, List.of());

    assertThatThrownBy(() -> guard.assertAccess("tenant-1", principal))
        .isInstanceOf(AccessDeniedException.class)
        .hasMessage("Tenant claim is required");
  }

  @Test
  void missingTenantClaimsAreDenied() {
    OAuth2User principal = principalWithClaims(null, null);

    assertThatThrownBy(() -> guard.assertAccess("tenant-1", principal))
        .isInstanceOf(AccessDeniedException.class)
        .hasMessage("Tenant claim is required");
  }

  private OAuth2User principalWithClaims(Object tenantId, Object tenantIdFallback) {
    OAuth2User principal = Mockito.mock(OAuth2User.class);
    when(principal.getAttribute("tenantId")).thenReturn(tenantId);
    when(principal.getAttribute("tenant_id")).thenReturn(tenantIdFallback);
    return principal;
  }
}
