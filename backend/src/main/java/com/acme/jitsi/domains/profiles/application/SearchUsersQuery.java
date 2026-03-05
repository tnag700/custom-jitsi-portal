package com.acme.jitsi.domains.profiles.application;

public record SearchUsersQuery(
    String tenantId,
    String query,
    String organization) {
}
