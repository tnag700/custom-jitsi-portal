/* eslint-disable qwik/loader-location */

import { routeAction$, z, zod$ } from "@qwik.dev/router";
import {
  InviteServiceError,
  createInvite,
  createInviteSchema,
  revokeInvite,
} from "~/lib/domains/invites";
import {
  buildMutationRequestContext,
  mapRouteActionError,
} from "~/lib/shared/routes/server-handlers";

export const useCreateInvite = routeAction$(
  async (data, { sharedMap, cookie, fail }) => {
    const requestContext = await buildMutationRequestContext({
      sharedMap,
      cookie,
    });

    try {
      const invite = await createInvite(requestContext, data.meetingId, {
        role: data.role,
        maxUses: data.maxUses,
        expiresInHours: data.expiresInHours,
      });
      return { success: true as const, invite };
    } catch (error) {
      return mapRouteActionError(
        error,
        InviteServiceError,
        fail,
        "INVITE_UNKNOWN",
      );
    }
  },
  zod$(
    createInviteSchema.extend({
      meetingId: z.string().min(1, "meetingId обязателен"),
    }),
  ),
);

export const useRevokeInvite = routeAction$(
  async (data, { sharedMap, cookie, fail }) => {
    const requestContext = await buildMutationRequestContext({
      sharedMap,
      cookie,
    });

    try {
      await revokeInvite(requestContext, data.meetingId, data.inviteId);
      return { success: true as const };
    } catch (error) {
      return mapRouteActionError(
        error,
        InviteServiceError,
        fail,
        "INVITE_UNKNOWN",
      );
    }
  },
  zod$(z.object({ meetingId: z.string().min(1), inviteId: z.string().min(1) })),
);
