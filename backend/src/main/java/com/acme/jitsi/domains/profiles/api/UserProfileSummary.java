package com.acme.jitsi.domains.profiles.api;

record UserProfileSummary(
    String subjectId,
    String fullName,
    String organization,
    String position) {
}
