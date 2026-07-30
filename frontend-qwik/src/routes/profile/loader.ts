import { routeLoader$ } from "@qwik.dev/router";
import {
  fetchMyProfile,
  ProfileServiceError,
  type ProfileErrorPayload,
  type UserProfileResponse,
} from "~/lib/domains/profile";
import { resolveAuthRecoveryRedirectPath } from "~/lib/domains/auth";
import { buildServerRequestContext } from "~/lib/shared/routes/server-handlers";

export interface ProfileLoaderData {
  profile: UserProfileResponse | null;
  isFirstRun: boolean;
  loadError: ProfileErrorPayload | null;
}

// eslint-disable-next-line qwik/loader-location
export const useMyProfile = routeLoader$(
  async ({ sharedMap, cookie, redirect, url }) => {
    const requestContext = buildServerRequestContext({ sharedMap, cookie });
    const returnTo = `${url.pathname}${url.search}`;
    try {
      const profile = await fetchMyProfile(requestContext);
      if (profile === null) {
        return {
          profile: null,
          isFirstRun: true,
          loadError: null,
        } satisfies ProfileLoaderData;
      }
      return {
        profile,
        isFirstRun: false,
        loadError: null,
      } satisfies ProfileLoaderData;
    } catch (error) {
      if (error instanceof ProfileServiceError) {
        if (error.payload.errorCode === "AUTH_REQUIRED") {
          throw redirect(302, resolveAuthRecoveryRedirectPath(error, returnTo));
        }
        return {
          profile: null,
          isFirstRun: false,
          loadError: error.payload,
        } satisfies ProfileLoaderData;
      }
      return {
        profile: null,
        isFirstRun: false,
        loadError: {
          title: "Ошибка загрузки",
          detail: "Не удалось загрузить профиль.",
          errorCode: "PROFILE_SERVICE_UNAVAILABLE",
        },
      } satisfies ProfileLoaderData;
    }
  },
);
