package com.acme.jitsi.domains.admin.service;

public record MonitoredFramework(
    String key,
    String displayName,
    String ecosystem,
    String packageName,
    String currentVersion,
    String versionSource) {
}
