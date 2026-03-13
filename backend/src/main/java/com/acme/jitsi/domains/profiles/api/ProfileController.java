package com.acme.jitsi.domains.profiles.api;

import com.acme.jitsi.domains.profiles.application.GetMyProfileUseCase;
import com.acme.jitsi.domains.profiles.application.UpsertMyProfileUseCase;
import com.acme.jitsi.domains.profiles.application.UpsertProfileCommand;
import com.acme.jitsi.domains.profiles.service.UserProfile;
import com.acme.jitsi.security.TenantAccessGuard;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/profile", version = "v1")
class ProfileController {

  private final GetMyProfileUseCase getMyProfileUseCase;
  private final UpsertMyProfileUseCase upsertMyProfileUseCase;
  private final TenantAccessGuard tenantAccessGuard;

  ProfileController(
      GetMyProfileUseCase getMyProfileUseCase,
      UpsertMyProfileUseCase upsertMyProfileUseCase,
      TenantAccessGuard tenantAccessGuard) {
    this.getMyProfileUseCase = getMyProfileUseCase;
    this.upsertMyProfileUseCase = upsertMyProfileUseCase;
    this.tenantAccessGuard = tenantAccessGuard;
  }

  @GetMapping("/me")
  UserProfileResponse getMyProfile(@AuthenticationPrincipal OAuth2User principal) {
    String subjectId = principal.getName();
    UserProfile profile = getMyProfileUseCase.execute(subjectId);
    return toResponse(profile);
  }

  @PutMapping("/me")
  UserProfileResponse upsertMyProfile(
      @Valid @RequestBody UpsertProfileRequest request,
      @AuthenticationPrincipal OAuth2User principal) {
    String subjectId = principal.getName();
    String tenantId = tenantAccessGuard.resolveTenantId(principal);

    UpsertProfileCommand command = new UpsertProfileCommand(
        subjectId,
        tenantId,
        request.fullName(),
        request.organization(),
        request.position());

    UserProfile profile = upsertMyProfileUseCase.execute(command);
    return toResponse(profile);
  }

  private UserProfileResponse toResponse(UserProfile profile) {
    return new UserProfileResponse(
        profile.subjectId(),
        profile.tenantId(),
        profile.fullName(),
        profile.organization(),
        profile.position(),
        profile.createdAt(),
        profile.updatedAt());
  }

}
