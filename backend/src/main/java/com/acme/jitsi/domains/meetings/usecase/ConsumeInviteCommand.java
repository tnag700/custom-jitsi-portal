package com.acme.jitsi.domains.meetings.usecase;

@org.springframework.modulith.NamedInterface(value = "usecase", propagate = false)
public record ConsumeInviteCommand(String token) {
}
