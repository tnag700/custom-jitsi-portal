package com.acme.jitsi.domains.profiles.api;

import com.acme.jitsi.domains.profiles.application.GetMyProfileUseCase;
import com.acme.jitsi.domains.profiles.application.UpsertMyProfileUseCase;
import com.acme.jitsi.domains.profiles.application.UpsertProfileCommand;
import com.acme.jitsi.domains.profiles.service.UserProfile;
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

  ProfileController(
      GetMyProfileUseCase getMyProfileUseCase,
      UpsertMyProfileUseCase upsertMyProfileUseCase) {
    this.getMyProfileUseCase = getMyProfileUseCase;
    this.upsertMyProfileUseCase = upsertMyProfileUseCase;
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
    String tenantId = resolveTenantId(principal);

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

  private String resolveTenantId(OAuth2User principal) {
    Object tenantIdClaim = principal.getAttribute("tenantId");
    if (tenantIdClaim == null) {
      tenantIdClaim = principal.getAttribute("tenant_id");
    }
    if (tenantIdClaim == null) {
      throw new IllegalStateException("Tenant claim is required");
    }
    if (tenantIdClaim instanceof java.util.Collection<?> coll) {
      return coll.isEmpty() ? "" : coll.iterator().next().toString();
    }
    return tenantIdClaim.toString();
  }
}
