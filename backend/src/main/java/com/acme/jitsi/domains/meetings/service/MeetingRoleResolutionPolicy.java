package com.acme.jitsi.domains.meetings.service;

import java.util.Optional;

interface MeetingRoleResolutionPolicy {

  Optional<MeetingRole> resolve(MeetingRoleResolutionContext context);
}