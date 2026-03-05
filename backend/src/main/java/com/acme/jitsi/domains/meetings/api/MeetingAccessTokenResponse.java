package com.acme.jitsi.domains.meetings.api;

import java.time.Instant;

record MeetingAccessTokenResponse(String joinUrl, Instant expiresAt, String role) {
}
