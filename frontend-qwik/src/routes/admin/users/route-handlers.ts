import { routeAction$, routeLoader$, z, zod$ } from "@qwik.dev/router";
import {
  fetchAdminUserProfiles,
  profileFormSchema,
  ProfileServiceError,
  updateAdminUserProfile,
  type ProfileErrorPayload,
  type UserProfileResponse,
} from "~/lib/domains/profile";
import {
  resolveAuthRecoveryRedirectPath,
  type SafeUserProfile,
} from "~/lib/domains/auth";
import { hasPlatformAdminAccess } from "~/lib/shared/security";
import {
  buildMutationRequestContext,
  buildServerRequestContext,
} from "~/lib/shared/routes/server-handlers";

export interface AdminUsersLoaderData {
  users: UserProfileResponse[];
  query: string;
  loadError: ProfileErrorPayload | null;
}

// eslint-disable-next-line qwik/loader-location
export const useAdminUsers = routeLoader$(
  async ({ sharedMap, cookie, query, redirect, url }) => {
    const user = (sharedMap.get("user") as SafeUserProfile | null) ?? null;
    const returnTo = `${url.pathname}${url.search}`;
    if (!user) {
      throw redirect(302, resolveAuthRecoveryRedirectPath(undefined, returnTo));
    }
    if (!hasPlatformAdminAccess(user.claims)) {
      throw redirect(302, "/admin");
    }

    const searchQuery = (query.get("q") ?? "").trim();
    try {
      return {
        users: await fetchAdminUserProfiles(
          buildServerRequestContext({ sharedMap, cookie }),
          searchQuery,
        ),
        query: searchQuery,
        loadError: null,
      } satisfies AdminUsersLoaderData;
    } catch (error) {
      if (error instanceof ProfileServiceError) {
        if (error.payload.errorCode === "AUTH_REQUIRED") {
          throw redirect(
            302,
            resolveAuthRecoveryRedirectPath(error, returnTo),
          );
        }
        if (error.payload.errorCode === "ACCESS_DENIED") {
          throw redirect(302, "/admin");
        }
        return {
          users: [],
          query: searchQuery,
          loadError: error.payload,
        } satisfies AdminUsersLoaderData;
      }
      throw error;
    }
  },
);

const adminUserProfileSchema = profileFormSchema.extend({
  subjectId: z.string().trim().min(1).max(255),
});

// eslint-disable-next-line qwik/loader-location
export const useUpdateAdminUserProfile = routeAction$(
  async (data, { sharedMap, cookie, fail, redirect, url }) => {
    const user = (sharedMap.get("user") as SafeUserProfile | null) ?? null;
    const returnTo = `${url.pathname}${url.search}`;
    if (!user) {
      throw redirect(302, resolveAuthRecoveryRedirectPath(undefined, returnTo));
    }
    if (!hasPlatformAdminAccess(user.claims)) {
      throw redirect(302, "/admin");
    }

    try {
      const profile = await updateAdminUserProfile(
        await buildMutationRequestContext({ sharedMap, cookie }),
        data.subjectId,
        {
          fullName: data.fullName,
          organization: data.organization,
          position: data.position,
        },
      );
      return { success: true as const, profile };
    } catch (error) {
      if (error instanceof ProfileServiceError) {
        if (error.payload.errorCode === "AUTH_REQUIRED") {
          throw redirect(
            302,
            resolveAuthRecoveryRedirectPath(error, returnTo),
          );
        }
        return fail(error.payload.errorCode === "ACCESS_DENIED" ? 403 : 400, {
          error: error.payload,
        });
      }
      return fail(500, {
        error: {
          title: "Ошибка обновления",
          detail: "Не удалось обновить профиль пользователя.",
          errorCode: "ADMIN_PROFILE_UPDATE_FAILED",
        },
      });
    }
  },
  zod$(adminUserProfileSchema),
);
