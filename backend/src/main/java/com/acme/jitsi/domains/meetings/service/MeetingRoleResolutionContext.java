package com.acme.jitsi.domains.meetings.service;

record MeetingRoleResolutionContext(
    String meetingId,
    String subject,
    MeetingTokenProperties properties) {
}