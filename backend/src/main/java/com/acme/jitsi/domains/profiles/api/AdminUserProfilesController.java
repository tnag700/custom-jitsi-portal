package com.acme.jitsi.domains.profiles.api;

import com.acme.jitsi.domains.profiles.application.SearchUsersQuery;
import com.acme.jitsi.domains.profiles.application.SearchUsersUseCase;
import com.acme.jitsi.domains.profiles.application.UpdateUserProfileCommand;
import com.acme.jitsi.domains.profiles.application.UpdateUserProfileUseCase;
import com.acme.jitsi.domains.profiles.service.UserProfile;
import com.acme.jitsi.security.TenantAccessGuard;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/admin/users", version = "v1")
class AdminUserProfilesController {

  private final SearchUsersUseCase searchUsersUseCase;
  private final UpdateUserProfileUseCase updateUserProfileUseCase;
  private final TenantAccessGuard tenantAccessGuard;

  AdminUserProfilesController(
      SearchUsersUseCase searchUsersUseCase,
      UpdateUserProfileUseCase updateUserProfileUseCase,
      TenantAccessGuard tenantAccessGuard) {
    this.searchUsersUseCase = searchUsersUseCase;
    this.updateUserProfileUseCase = updateUserProfileUseCase;
    this.tenantAccessGuard = tenantAccessGuard;
  }

  @GetMapping
  List<UserProfileResponse> search(
      @RequestParam(value = "q", required = false) @Nullable String query,
      @RequestParam(value = "organization", required = false) @Nullable String organization,
      @AuthenticationPrincipal OAuth2User principal) {
    String tenantId = tenantAccessGuard.resolveTenantId(principal);
    return searchUsersUseCase.execute(new SearchUsersQuery(tenantId, query, organization)).stream()
        .map(this::toResponse)
        .toList();
  }

  @PutMapping("/{subjectId}")
  UserProfileResponse update(
      @PathVariable String subjectId,
      @Valid @RequestBody UpsertProfileRequest request,
      @RequestHeader(value = "X-Trace-Id", required = false) @Nullable String traceId,
      @AuthenticationPrincipal OAuth2User principal) {
    String tenantId = tenantAccessGuard.resolveTenantId(principal);
    UserProfile profile = updateUserProfileUseCase.execute(new UpdateUserProfileCommand(
        principal.getName(),
        subjectId,
        tenantId,
        request.fullName(),
        request.organization(),
        request.position(),
        traceId == null || traceId.isBlank() ? UUID.randomUUID().toString() : traceId.trim()));
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
