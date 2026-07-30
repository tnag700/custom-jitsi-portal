import { routeAction$, z, zod$ } from "@qwik.dev/router";
import { issueAccessToken, JoinServiceError } from "~/lib/domains/join";
import { resolveAuthRecoveryRedirectPath } from "~/lib/domains/auth";
import { buildMutationRequestContext } from "~/lib/shared/routes/server-handlers";

// eslint-disable-next-line qwik/loader-location
export const useJoinMeeting = routeAction$(
  async (data, { sharedMap, cookie, redirect, fail, url }) => {
    const requestContext = await buildMutationRequestContext({
      sharedMap,
      cookie,
    });
    const returnTo = `${url.pathname}${url.search}`;
    try {
      return await issueAccessToken(requestContext, data.meetingId);
    } catch (error) {
      if (error instanceof JoinServiceError) {
        if (error.payload.errorCode === "AUTH_REQUIRED") {
          throw redirect(302, resolveAuthRecoveryRedirectPath(error, returnTo));
        }
        return fail(400, { error: error.payload });
      }
      throw error;
    }
  },
  zod$(z.object({ meetingId: z.string().min(1) })),
);
