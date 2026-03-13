package com.acme.jitsi.domains.auth.service;

import java.net.URI;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class AuthLogoutService {

  private final ObjectProvider<ClientRegistrationRepository> clientRegistrationRepositoryProvider;
  private final String frontendOrigin;
  private final String expectedIssuer;

  public AuthLogoutService(
      ObjectProvider<ClientRegistrationRepository> clientRegistrationRepositoryProvider,
      @Value("${app.frontend.origin:http://localhost:3000}") String frontendOrigin,
      @Value("${app.security.sso.expected-issuer:}") String expectedIssuer) {
    this.clientRegistrationRepositoryProvider = clientRegistrationRepositoryProvider;
    this.frontendOrigin = frontendOrigin;
    this.expectedIssuer = expectedIssuer;
  }

  public URI resolveLogoutRedirect(Authentication authentication) {
    URI fallbackUri = frontendAuthEntryUri();
    if (!(authentication instanceof OAuth2AuthenticationToken oauthToken)) {
      return fallbackUri;
    }
    if (!(oauthToken.getPrincipal() instanceof OidcUser oidcUser)) {
      return fallbackUri;
    }

    ClientRegistrationRepository clientRegistrationRepository =
        clientRegistrationRepositoryProvider.getIfAvailable();
    ClientRegistration registration = clientRegistrationRepository == null
        ? null
        : clientRegistrationRepository.findByRegistrationId(oauthToken.getAuthorizedClientRegistrationId());

    String endSessionEndpoint = resolveEndSessionEndpoint(registration);
    if (!StringUtils.hasText(endSessionEndpoint)) {
      return fallbackUri;
    }

    return UriComponentsBuilder.fromUriString(endSessionEndpoint)
        .queryParam("id_token_hint", oidcUser.getIdToken().getTokenValue())
        .queryParam("post_logout_redirect_uri", fallbackUri.toString())
        .build(true)
        .toUri();
  }

  private String resolveEndSessionEndpoint(ClientRegistration registration) {
    if (registration != null) {
      Object configuredEndpoint = registration.getProviderDetails().getConfigurationMetadata().get("end_session_endpoint");
      if (configuredEndpoint instanceof String endpoint && StringUtils.hasText(endpoint)) {
        return endpoint;
      }

      String issuerUri = registration.getProviderDetails().getIssuerUri();
      if (StringUtils.hasText(issuerUri)) {
        return appendKeycloakLogoutPath(issuerUri);
      }

      String authorizationUri = registration.getProviderDetails().getAuthorizationUri();
      if (StringUtils.hasText(authorizationUri) && authorizationUri.endsWith("/auth")) {
        return authorizationUri.substring(0, authorizationUri.length() - "/auth".length()) + "/logout";
      }
    }

    if (StringUtils.hasText(expectedIssuer)) {
      return appendKeycloakLogoutPath(expectedIssuer);
    }

    return null;
  }

  private URI frontendAuthEntryUri() {
    String normalizedOrigin = frontendOrigin.endsWith("/")
        ? frontendOrigin.substring(0, frontendOrigin.length() - 1)
        : frontendOrigin;
    return URI.create(normalizedOrigin + "/auth");
  }

  private String appendKeycloakLogoutPath(String issuerUri) {
    String normalizedIssuer = issuerUri.endsWith("/")
        ? issuerUri.substring(0, issuerUri.length() - 1)
        : issuerUri;
    if (normalizedIssuer.endsWith("/logout")) {
      return normalizedIssuer;
    }
    if (normalizedIssuer.endsWith("/protocol/openid-connect")) {
      return normalizedIssuer + "/logout";
    }
    return normalizedIssuer + "/protocol/openid-connect/logout";
  }
}