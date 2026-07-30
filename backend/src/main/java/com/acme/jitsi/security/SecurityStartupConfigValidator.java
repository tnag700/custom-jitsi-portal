package com.acme.jitsi.security;

import java.net.URI;
import java.net.URISyntaxException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
class SecurityStartupConfigValidator implements InitializingBean {

  private static final String INSECURE_SSO_SECRET_PLACEHOLDER = "change-me";

  private final String frontendOrigin;
  private final String ssoClientSecret;
  private final boolean allowInsecurePrivateOrigin;

  @Autowired
  SecurityStartupConfigValidator(
      @Value("${app.frontend.origin:http://localhost:3000}") String frontendOrigin,
      @Value("${SSO_CLIENT_SECRET:}") String ssoClientSecret,
      @Value("${app.security.allow-insecure-private-origin:false}") boolean allowInsecurePrivateOrigin) {
    this.frontendOrigin = frontendOrigin;
    this.ssoClientSecret = ssoClientSecret;
    this.allowInsecurePrivateOrigin = allowInsecurePrivateOrigin;
  }

  SecurityStartupConfigValidator(String frontendOrigin, String ssoClientSecret) {
    this(frontendOrigin, ssoClientSecret, false);
  }

  @Override
  public void afterPropertiesSet() {
    validateFrontendOrigin(frontendOrigin);
    validateSsoClientSecret(ssoClientSecret);
  }

  private void validateFrontendOrigin(String rawOrigin) {
    String origin = normalizeOrigin(rawOrigin);
    rejectWildcardOrigin(origin);

    URI uri = parseOrigin(origin);
    validateScheme(uri);
    validateHost(uri);
    validateTransportSecurity(uri);
    validatePath(uri);
    validateOriginComponents(uri);
  }

  private String normalizeOrigin(String rawOrigin) {
    String origin = rawOrigin == null ? "" : rawOrigin.trim();
    if (origin.isEmpty()) {
      throw new IllegalStateException("app.frontend.origin must not be blank.");
    }
    return origin;
  }

  private void rejectWildcardOrigin(String origin) {
    if (origin.contains("*")) {
      throw new IllegalStateException("app.frontend.origin must not contain wildcards.");
    }
  }

  private URI parseOrigin(String origin) {
    try {
      return new URI(origin);
    } catch (URISyntaxException ex) {
      throw new IllegalStateException("app.frontend.origin must be a valid absolute origin URL.", ex);
    }
  }

  private void validateScheme(URI uri) {
    String scheme = uri.getScheme();
    if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
      throw new IllegalStateException("app.frontend.origin must use http or https.");
    }
  }

  private void validateHost(URI uri) {
    if (uri.getHost() == null || uri.getHost().isBlank()) {
      throw new IllegalStateException("app.frontend.origin must include a host.");
    }
  }

  private void validateTransportSecurity(URI uri) {
    if ("http".equalsIgnoreCase(uri.getScheme())
        && !isLocalhost(uri.getHost())
        && !(allowInsecurePrivateOrigin && isPrivateIpv4(uri.getHost()))) {
      throw new IllegalStateException("app.frontend.origin must use https outside localhost.");
    }
  }

  private void validatePath(URI uri) {
    if (uri.getRawPath() != null && !uri.getRawPath().isEmpty() && !"/".equals(uri.getRawPath())) {
      throw new IllegalStateException("app.frontend.origin must not include a path.");
    }
  }

  private void validateOriginComponents(URI uri) {
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

  private boolean isPrivateIpv4(String host) {
    return host.matches(
        "(?:10(?:\\.\\d{1,3}){3}|192\\.168(?:\\.\\d{1,3}){2}|172\\.(?:1[6-9]|2\\d|3[01])(?:\\.\\d{1,3}){2})");
  }
}
