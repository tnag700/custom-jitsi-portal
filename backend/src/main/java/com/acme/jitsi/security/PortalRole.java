package com.acme.jitsi.security;

import java.util.Optional;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * Platform roles that are allowed to influence portal authorization.
 *
 * <p>Identity providers often include roles for multiple clients in one token.
 * Keeping the allow-list here prevents an unrelated client role from becoming a
 * Spring {@code ROLE_*} authority in the portal.
 */
enum PortalRole {
  ADMIN("admin"),
  SYSTEM_ADMIN("system-admin"),
  SECURITY_ADMIN("security-admin"),
  SUPPORT_ENGINEER("support-engineer"),
  PARTICIPANT("participant");

  private final String roleName;

  PortalRole(String roleName) {
    this.roleName = roleName;
  }

  String claimValue() {
    return roleName;
  }

  SimpleGrantedAuthority authority() {
    return new SimpleGrantedAuthority("ROLE_" + roleName);
  }

  static Optional<PortalRole> fromClaim(Object rawRole) {
    if (!(rawRole instanceof String roleName)) {
      return Optional.empty();
    }

    String normalized = roleName.trim();
    for (PortalRole role : values()) {
      if (role.roleName.equals(normalized)) {
        return Optional.of(role);
      }
    }
    return Optional.empty();
  }
}
