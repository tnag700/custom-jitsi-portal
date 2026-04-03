package com.acme.jitsi.security;

import java.net.URI;
import java.net.URISyntaxException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
class SecurityStartupConfigValidator implements InitializingBean {

  private static final String INSECURE_SSO_SECRET_PLACEHOLDER = "change-me";

  private final String frontendOrigin;
  private final String ssoClientSecret;

  SecurityStartupConfigValidator(
      @Value("${app.frontend.origin:http://localhost:3000}") String frontendOrigin,
      @Value("${SSO_CLIENT_SECRET:}") String ssoClientSecret) {
    this.frontendOrigin = frontendOrigin;
    this.ssoClientSecret = ssoClientSecret;
  }

  @Override
  public void afterPropertiesSet() {
    validateFrontendOrigin(frontendOrigin);
    validateSsoClientSecret(ssoClientSecret);
  }

  private void validateFrontendOrigin(String rawOrigin) {
    String origin = rawOrigin == null ? "" : rawOrigin.trim();
    if (origin.isEmpty()) {
      throw new IllegalStateException("app.frontend.origin must not be blank.");
    }
    if (origin.contains("*")) {
      throw new IllegalStateException("app.frontend.origin must not contain wildcards.");
    }

    URI uri;
    try {
      uri = new URI(origin);
    } catch (URISyntaxException ex) {
      throw new IllegalStateException("app.frontend.origin must be a valid absolute origin URL.", ex);
    }

    String scheme = uri.getScheme();
    if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
      throw new IllegalStateException("app.frontend.origin must use http or https.");
    }
    if (uri.getHost() == null || uri.getHost().isBlank()) {
      throw new IllegalStateException("app.frontend.origin must include a host.");
    }
    if ("http".equalsIgnoreCase(scheme) && !isLocalhost(uri.getHost())) {
      throw new IllegalStateException("app.frontend.origin must use https outside localhost.");
    }
    if (uri.getRawPath() != null && !uri.getRawPath().isEmpty() && !"/".equals(uri.getRawPath())) {
      throw new IllegalStateException("app.frontend.origin must not include a path.");
    }
    if (uri.getRawQuery() != null || uri.getRawFragment() != null || uri.getRawUserInfo() != null) {
      throw new IllegalStateException("app.frontend.origin must not include userinfo, query, or fragment.");
    }
  }

  private void validateSsoClientSecret(String secret) {
    String normalizedSecret = secret == null ? "" : secret.trim();
    if (INSECURE_SSO_SECRET_PLACEHOLDER.equalsIgnoreCase(normalizedSecret)) {
      throw new IllegalStateException(
          "Insecure SSO client secret placeholder detected. Set SSO_CLIENT_SECRET to a real secret.");
    }
  }

  private boolean isLocalhost(String host) {
    return "localhost".equalsIgnoreCase(host)
        || "127.0.0.1".equals(host)
        || "::1".equals(host)
        || "[::1]".equals(host);
  }
}
