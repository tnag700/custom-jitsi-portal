package com.acme.jitsi.domains.admin.service;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

final class IncidentSubjectPolicy {

  private IncidentSubjectPolicy() {
  }

  static boolean canViewFullSubject(Collection<String> authorities) {
    Set<String> normalized = authorities == null
        ? Set.of()
        : authorities.stream()
            .filter(Objects::nonNull)
            .map(value -> value.toLowerCase(Locale.ROOT))
            .collect(LinkedHashSet::new, Set::add, Set::addAll);
    return normalized.contains("role_security-admin")
        || normalized.contains("security-admin")
        || normalized.contains("role_system-admin")
        || normalized.contains("system-admin");
  }

  static String maskSubject(String subjectId) {
    if (!IncidentNormalizationPolicy.hasText(subjectId)) {
      return null;
    }
    String value = subjectId.trim();
    return value.length() <= 4
        ? "***"
        : value.substring(0, 3) + "***" + value.substring(value.length() - 2);
  }
}