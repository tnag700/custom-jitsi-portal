/* eslint-disable qwik/loader-location */

import { routeAction$, z, zod$ } from "@qwik.dev/router";
import {
  MeetingServiceError,
  cancelMeeting,
  createMeeting,
  createMeetingSchema,
  updateMeeting,
  updateMeetingSchema,
} from "~/lib/domains/meetings";
import {
  buildMutationRequestContext,
  mapRouteActionError,
} from "~/lib/shared/routes/server-handlers";

export const useCreateMeeting = routeAction$(
  async (data, { sharedMap, cookie, fail }) => {
    const requestContext = await buildMutationRequestContext({
      sharedMap,
      cookie,
    });

    try {
      const meeting = await createMeeting(requestContext, data.roomId, {
        title: data.title,
        description: data.description,
        meetingType: data.meetingType,
        startsAt: data.startsAt,
        endsAt: data.endsAt,
        allowGuests: data.allowGuests,
        recordingEnabled: data.recordingEnabled,
      });
      return { success: true as const, meeting };
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
    createMeetingSchema.and(
      z.object({ roomId: z.string().min(1, "roomId обязателен") }),
    ),
  ),
);

export const useUpdateMeeting = routeAction$(
  async (data, { sharedMap, cookie, fail }) => {
    const requestContext = await buildMutationRequestContext({
      sharedMap,
      cookie,
    });

    try {
      const meeting = await updateMeeting(requestContext, data.meetingId, {
        title: data.title,
        description: data.description,
        meetingType: data.meetingType,
        startsAt: data.startsAt,
        endsAt: data.endsAt,
        allowGuests: data.allowGuests,
        recordingEnabled: data.recordingEnabled,
      });
      return { success: true as const, meeting };
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
    updateMeetingSchema.and(
      z.object({ meetingId: z.string().min(1, "meetingId обязателен") }),
    ),
  ),
);

export const useCancelMeeting = routeAction$(
  async (data, { sharedMap, cookie, fail }) => {
    const requestContext = await buildMutationRequestContext({
      sharedMap,
      cookie,
    });

    try {
      const meeting = await cancelMeeting(requestContext, data.meetingId);
      return { success: true as const, meeting };
    } catch (error) {
      return mapRouteActionError(
        error,
        MeetingServiceError,
        fail,
        "MEETING_UNKNOWN",
      );
    }
  },
  zod$(z.object({ meetingId: z.string().min(1, "meetingId обязателен") })),
);
