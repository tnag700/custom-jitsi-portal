package com.acme.jitsi.domains.configsets.service;

public record ConfigCompatibilityMismatch(
    ConfigCompatibilityMismatchCode code,
    String message,
    String expected,
    String actual) {
}