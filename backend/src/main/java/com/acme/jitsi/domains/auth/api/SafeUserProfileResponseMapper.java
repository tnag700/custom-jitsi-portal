package com.acme.jitsi.domains.auth.api;

import java.util.Collection;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;

@Component
public class SafeUserProfileResponseMapper {

  public SafeUserProfileResponse fromPrincipal(OAuth2User principal) {
    String id = firstNonBlank(claim(principal, "sub"), principal.getName());
    String displayName = firstNonBlank(claim(principal, "name"), claim(principal, "preferred_username"), id);
    String email = firstNonBlank(claim(principal, "email"), "");
    String tenant = firstNonBlank(claim(principal, "tenantId"), claim(principal, "tenant_id"),
        claim(principal, "tenant"), claim(principal, "hd"), "default");

    Collection<String> claims = principal.getAuthorities().stream()
        .map(GrantedAuthority::getAuthority)
        .filter(Objects::nonNull)
        .sorted()
        .collect(Collectors.toList());

    return new SafeUserProfileResponse(id, displayName, email, tenant, claims);
  }

  private String claim(OAuth2User principal, String claimName) {
    Object value = principal.getAttribute(claimName);
    return value == null ? "" : value.toString();
  }

  private String firstNonBlank(String... values) {
    for (String value : values) {
      if (value != null && !value.isBlank()) {
        return value;
      }
    }
    return "";
  }
}