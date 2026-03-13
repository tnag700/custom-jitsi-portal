package com.acme.jitsi.domains.meetings.service;

import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

public interface MeetingProfilesPort {

  @Nullable
  MeetingProfileSnapshot findBySubjectId(String subjectId);

  Map<String, MeetingProfileSnapshot> findBySubjectIds(List<String> subjectIds);
}