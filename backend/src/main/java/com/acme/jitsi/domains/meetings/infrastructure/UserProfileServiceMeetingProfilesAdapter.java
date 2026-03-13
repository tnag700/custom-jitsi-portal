package com.acme.jitsi.domains.meetings.infrastructure;

import com.acme.jitsi.domains.meetings.service.MeetingProfileSnapshot;
import com.acme.jitsi.domains.meetings.service.MeetingProfilesPort;
import com.acme.jitsi.domains.profiles.service.ProfileNotFoundException;
import com.acme.jitsi.domains.profiles.service.UserProfile;
import com.acme.jitsi.domains.profiles.service.UserProfileService;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class UserProfileServiceMeetingProfilesAdapter implements MeetingProfilesPort {

  private final UserProfileService userProfileService;

  public UserProfileServiceMeetingProfilesAdapter(UserProfileService userProfileService) {
    this.userProfileService = userProfileService;
  }

  @Override
  public MeetingProfileSnapshot findBySubjectId(String subjectId) {
    try {
      UserProfile profile = userProfileService.getBySubjectId(subjectId);
      return toSnapshot(profile);
    } catch (ProfileNotFoundException ignored) {
      return null;
    } catch (RuntimeException ignored) {
      return null;
    }
  }

  @Override
  public Map<String, MeetingProfileSnapshot> findBySubjectIds(List<String> subjectIds) {
    return userProfileService.findBySubjectIds(subjectIds).stream()
        .map(this::toSnapshot)
        .collect(Collectors.toMap(MeetingProfileSnapshot::subjectId, Function.identity()));
  }

  private MeetingProfileSnapshot toSnapshot(UserProfile profile) {
    return new MeetingProfileSnapshot(
        profile.subjectId(),
        profile.fullName(),
        profile.organization(),
        profile.position());
  }
}