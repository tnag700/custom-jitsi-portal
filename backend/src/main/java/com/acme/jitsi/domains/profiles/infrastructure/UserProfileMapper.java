package com.acme.jitsi.domains.profiles.infrastructure;

import com.acme.jitsi.domains.profiles.service.UserProfile;

public final class UserProfileMapper {

  private UserProfileMapper() {
  }

  public static UserProfile toDomain(UserProfileEntity entity) {
    return entity.toDomain();
  }

  public static UserProfileEntity toEntity(UserProfile profile) {
    return new UserProfileEntity(profile);
  }
}
