import { routeAction$, zod$ } from "@qwik.dev/router";
import {
  profileFormSchema,
  ProfileServiceError,
  upsertMyProfile,
} from "~/lib/domains/profile";
import { resolveAuthRecoveryRedirectPath } from "~/lib/domains/auth";
import { buildMutationRequestContext } from "~/lib/shared/routes/server-handlers";

// eslint-disable-next-line qwik/loader-location
export const useUpsertProfile = routeAction$(
  async (data, { sharedMap, cookie, redirect, fail, url }) => {
    const requestContext = await buildMutationRequestContext({
      sharedMap,
      cookie,
    });
    const returnTo = `${url.pathname}${url.search}`;
    try {
      const profile = await upsertMyProfile(requestContext, data);
      return { success: true as const, profile };
    } catch (error) {
      if (error instanceof ProfileServiceError) {
        if (error.payload.errorCode === "AUTH_REQUIRED") {
          throw redirect(302, resolveAuthRecoveryRedirectPath(error, returnTo));
        }
        return fail(400, { error: error.payload });
      }
      return fail(500, {
        error: {
          title: "Ошибка",
          detail: "Неизвестная ошибка",
          errorCode: "PROFILE_UNKNOWN",
        },
      });
    }
  },
  zod$(profileFormSchema),
);
