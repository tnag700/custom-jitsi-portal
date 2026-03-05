package com.acme.jitsi.domains.profiles.api;

import com.acme.jitsi.domains.profiles.application.SearchUsersQuery;
import com.acme.jitsi.domains.profiles.application.SearchUsersUseCase;
import com.acme.jitsi.domains.profiles.service.UserProfile;
import com.acme.jitsi.security.TenantAccessGuard;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/users", version = "v1")
class UserSearchController {

  private final SearchUsersUseCase searchUsersUseCase;
  private final TenantAccessGuard tenantAccessGuard;

  UserSearchController(
      SearchUsersUseCase searchUsersUseCase,
      TenantAccessGuard tenantAccessGuard) {
    this.searchUsersUseCase = searchUsersUseCase;
    this.tenantAccessGuard = tenantAccessGuard;
  }

  @GetMapping("/search")
  List<UserProfileSummary> searchUsers(
      @RequestParam("tenant_id") String tenantId,
      @RequestParam(value = "q", required = false) @Nullable String query,
      @RequestParam(value = "organization", required = false) @Nullable String organization,
      @AuthenticationPrincipal OAuth2User principal) {

    tenantAccessGuard.assertAccess(tenantId, principal);

    SearchUsersQuery searchQuery = new SearchUsersQuery(tenantId, query, organization);
    List<UserProfile> results = searchUsersUseCase.execute(searchQuery);

    return results.stream()
        .map(p -> new UserProfileSummary(p.subjectId(), p.fullName(), p.organization(), p.position()))
        .toList();
  }
}
