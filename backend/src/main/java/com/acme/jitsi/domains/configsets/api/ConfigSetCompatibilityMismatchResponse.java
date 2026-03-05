package com.acme.jitsi.domains.configsets.api;

public record ConfigSetCompatibilityMismatchResponse(
    String code,
    String message,
    String expected,
    String actual) {
}