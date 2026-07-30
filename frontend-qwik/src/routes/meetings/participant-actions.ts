/* eslint-disable qwik/loader-location */

import { routeAction$, z, zod$ } from "@qwik.dev/router";
import {
  MeetingServiceError,
  assignParticipant,
  assignParticipantSchema,
  bulkAssignParticipants,
  bulkAssignParticipantsSchema,
  unassignParticipant,
  updateParticipantRole,
  updateParticipantRoleSchema,
} from "~/lib/domains/meetings";
import {
  buildMutationRequestContext,
  mapRouteActionError,
} from "~/lib/shared/routes/server-handlers";

export const useAssignParticipant = routeAction$(
  async (data, { sharedMap, cookie, fail }) => {
    const requestContext = await buildMutationRequestContext({
      sharedMap,
      cookie,
    });

    try {
      const assignment = await assignParticipant(
        requestContext,
        data.meetingId,
        { subjectId: data.subjectId, role: data.role },
      );
      return { success: true as const, assignment };
    } catch (error) {
      return mapRouteActionError(
        error,
        MeetingServiceError,
        fail,
        "MEETING_UNKNOWN",
      );
    }
  },
  zod$(
    assignParticipantSchema.extend({
      meetingId: z.string().min(1, "meetingId обязателен"),
    }),
  ),
);

export const useBulkAssignParticipants = routeAction$(
  async (data, { sharedMap, cookie, fail }) => {
    const requestContext = await buildMutationRequestContext({
      sharedMap,
      cookie,
    });

    try {
      const assignments = await bulkAssignParticipants(
        requestContext,
        data.meetingId,
        {
          defaultRole: data.defaultRole,
          participants: data.subjectIds.map((subjectId) => ({ subjectId })),
        },
      );
      return { success: true as const, assignments };
    } catch (error) {
      return mapRouteActionError(
        error,
        MeetingServiceError,
        fail,
        "MEETING_UNKNOWN",
      );
    }
  },
  zod$(
    bulkAssignParticipantsSchema.extend({
      meetingId: z.string().min(1, "meetingId обязателен"),
    }),
  ),
);

export const useUpdateParticipantRole = routeAction$(
  async (data, { sharedMap, cookie, fail }) => {
    const requestContext = await buildMutationRequestContext({
      sharedMap,
      cookie,
    });

    try {
      const assignment = await updateParticipantRole(
        requestContext,
        data.meetingId,
        data.subjectId,
        { role: data.role },
      );
      return { success: true as const, assignment };
    } catch (error) {
      return mapRouteActionError(
        error,
        MeetingServiceError,
        fail,
        "MEETING_UNKNOWN",
      );
    }
  },
  zod$(
    updateParticipantRoleSchema.extend({
      meetingId: z.string().min(1, "meetingId обязателен"),
      subjectId: z.string().min(1, "subjectId обязателен"),
    }),
  ),
);

export const useUnassignParticipant = routeAction$(
  async (data, { sharedMap, cookie, fail }) => {
    const requestContext = await buildMutationRequestContext({
      sharedMap,
      cookie,
    });

    try {
      await unassignParticipant(requestContext, data.meetingId, data.subjectId);
      return { success: true as const };
    } catch (error) {
      return mapRouteActionError(
        error,
        MeetingServiceError,
        fail,
        "MEETING_UNKNOWN",
      );
    }
  },
  zod$(
    z.object({
      meetingId: z.string().min(1, "meetingId обязателен"),
      subjectId: z.string().min(1, "subjectId обязателен"),
    }),
  ),
);
