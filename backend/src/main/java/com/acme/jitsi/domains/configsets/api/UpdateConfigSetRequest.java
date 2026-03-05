package com.acme.jitsi.domains.configsets.api;

import com.acme.jitsi.domains.configsets.service.ConfigSetEnvironmentType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

record UpdateConfigSetRequest(
    @NotBlank @Size(max = 255) String name,
    @NotBlank String tenantId,
    @NotNull ConfigSetEnvironmentType environmentType,
    @NotBlank String issuer,
    @NotBlank String audience,
    @NotBlank String algorithm,
    @Size(max = 255) String roleClaim,
    String signingSecret,
    String jwksUri,
    @Min(1) int accessTtlMinutes,
    Integer refreshTtlMinutes,
    @NotBlank String meetingsServiceUrl) {
}