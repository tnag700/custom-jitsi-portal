package com.acme.jitsi.domains.rooms.infrastructure;

import com.acme.jitsi.domains.rooms.service.ConfigSetValidator;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Validates selected config-set against allowed IDs and JWT contour consistency.
 */
@Component
@ConditionalOnProperty(name = "app.features.config-sets-from-db", havingValue = "false", matchIfMissing = true)
class DefaultConfigSetValidator implements ConfigSetValidator {

  private static final String CONFIG_SETS_PREFIX = "app.rooms.config-sets.";

  private final Set<String> validConfigSetIds;
  private final Binder binder;
  private final String meetingsIssuer;
  private final String meetingsAudience;
  private final String meetingsRoleClaim;
  private final String contourIssuer;
  private final String contourAudience;
  private final String contourRoleClaim;

  DefaultConfigSetValidator(
      Environment environment,
      @Value("${app.rooms.valid-config-sets:config-1,config-2}") String validConfigSets,
      @Value("${app.meetings.token.issuer:}") String meetingsIssuer,
      @Value("${app.meetings.token.audience:}") String meetingsAudience,
      @Value("${app.meetings.token.role-claim-name:}") String meetingsRoleClaim,
      @Value("${app.security.jwt-contour.issuer:}") String contourIssuer,
      @Value("${app.security.jwt-contour.audience:}") String contourAudience,
      @Value("${app.security.jwt-contour.role-claim:}") String contourRoleClaim) {
    this.binder = Binder.get(environment);
    this.validConfigSetIds = Arrays.stream(validConfigSets.split(","))
        .map(String::trim)
        .filter(value -> !value.isEmpty())
        .collect(Collectors.toUnmodifiableSet());
    this.meetingsIssuer = meetingsIssuer;
    this.meetingsAudience = meetingsAudience;
    this.meetingsRoleClaim = meetingsRoleClaim;
    this.contourIssuer = contourIssuer;
    this.contourAudience = contourAudience;
    this.contourRoleClaim = contourRoleClaim;
  }

  @Override
  public boolean isValid(String configSetId) {
    if (configSetId == null || configSetId.isBlank()) {
      return false;
    }

    String normalizedConfigSetId = configSetId.trim();
    if (!validConfigSetIds.contains(normalizedConfigSetId)) {
      return false;
    }

    ConfigSetDefinition configSet = binder
        .bind(CONFIG_SETS_PREFIX + normalizedConfigSetId, Bindable.of(ConfigSetDefinition.class))
        .orElse(null);

    if (configSet == null || !configSet.isComplete()) {
      return false;
    }

    return contourIsConsistent()
        && valuesEqual(configSet.issuer(), meetingsIssuer)
        && valuesEqual(configSet.audience(), meetingsAudience)
        && valuesEqual(configSet.roleClaim(), meetingsRoleClaim)
        && valuesEqual(configSet.issuer(), contourIssuer)
        && valuesEqual(configSet.audience(), contourAudience)
        && valuesEqual(configSet.roleClaim(), contourRoleClaim);
  }

  private boolean contourIsConsistent() {
    return valuesEqual(meetingsIssuer, contourIssuer)
        && valuesEqual(meetingsAudience, contourAudience)
        && valuesEqual(meetingsRoleClaim, contourRoleClaim);
  }

  private boolean valuesEqual(String left, String right) {
    String normalizedLeft = normalize(left);
    String normalizedRight = normalize(right);
    return !normalizedLeft.isEmpty() && normalizedLeft.equals(normalizedRight);
  }

  private String normalize(String value) {
    return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
  }

  static final class ConfigSetDefinition {
    private String issuer;
    private String audience;
    private String roleClaim;

    String issuer() {
      return issuer;
    }

    public void setIssuer(String issuer) {
      this.issuer = issuer;
    }

    String audience() {
      return audience;
    }

    public void setAudience(String audience) {
      this.audience = audience;
    }

    String roleClaim() {
      return roleClaim;
    }

    public void setRoleClaim(String roleClaim) {
      this.roleClaim = roleClaim;
    }

    boolean isComplete() {
      return !isBlank(issuer) && !isBlank(audience) && !isBlank(roleClaim);
    }

    private boolean isBlank(String value) {
      return value == null || value.isBlank();
    }
  }
}
