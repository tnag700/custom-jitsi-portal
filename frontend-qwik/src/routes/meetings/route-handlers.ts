/* eslint-disable qwik/loader-location */

import { routeAction$, routeLoader$, z, zod$ } from "@qwik.dev/router";
import type { SafeUserProfile } from "~/lib/domains/auth";
import {
  MeetingServiceError,
  assignParticipant,
  assignParticipantSchema,
  bulkAssignParticipants,
  bulkAssignParticipantsSchema,
  cancelMeeting,
  createMeeting,
  createMeetingSchema,
  fetchMeetings,
  fetchParticipants,
  searchUsers,
  unassignParticipant,
  updateMeeting,
  updateMeetingSchema,
  updateParticipantRole,
  updateParticipantRoleSchema,
} from "~/lib/domains/meetings";
import {
  InviteServiceError,
  createInvite,
  createInviteSchema,
  fetchInvites,
  revokeInvite,
} from "~/lib/domains/invites";
import { fetchRooms } from "~/lib/domains/rooms";
import {
  buildMutationRequestContext,
  buildServerRequestContext,
  mapRouteActionError,
} from "~/lib/shared/routes/server-handlers";

const emptyMeetingsPage = {
  content: [],
  page: 0,
  pageSize: 20,
  totalElements: 0,
  totalPages: 0,
};

const emptyInvitesPage = {
  content: [],
  page: 0,
  pageSize: 20,
  totalElements: 0,
  totalPages: 0,
};

export const useActiveRooms = routeLoader$(async ({ sharedMap, cookie }) => {
  const user = sharedMap.get("user") as SafeUserProfile;
  const requestContext = buildServerRequestContext({ sharedMap, cookie });

  try {
    const rooms = await fetchRooms(requestContext, user.tenant);
    return {
      ...rooms,
      content: rooms.content.filter((room) => room.status === "active"),
    };
  } catch {
    return emptyMeetingsPage;
  }
});

export const useMeetings = routeLoader$(async ({ sharedMap, cookie, query }) => {
  const requestContext = buildServerRequestContext({ sharedMap, cookie });
  const roomId = query.get("roomId");

  if (!roomId) {
    return emptyMeetingsPage;
  }

  try {
    return await fetchMeetings(requestContext, roomId);
  } catch {
    return emptyMeetingsPage;
  }
});

export const useParticipants = routeLoader$(async ({ sharedMap, cookie, query }) => {
  const requestContext = buildServerRequestContext({ sharedMap, cookie });
  const roomId = query.get("roomId");
  const meetingId = query.get("meetingId");
  const uuidLike =
    /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

  if (!roomId || !meetingId || !uuidLike.test(meetingId)) {
    return [];
  }

  try {
    return await fetchParticipants(requestContext, meetingId);
  } catch {
    return [];
  }
});

export const useAssignableUsers = routeLoader$(async ({ sharedMap, cookie, query }) => {
  const user = sharedMap.get("user") as SafeUserProfile;
  const requestContext = buildServerRequestContext({ sharedMap, cookie });
  const meetingId = query.get("meetingId");
  const uuidLike =
    /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

  if (!meetingId || !uuidLike.test(meetingId)) {
    return [];
  }

  const participantQuery = query.get("participantQuery") ?? undefined;
  const participantOrganization = query.get("participantOrganization") ?? undefined;

  try {
    return await searchUsers(requestContext, user.tenant, participantQuery, participantOrganization);
  } catch {
    return [];
  }
});

export const useInvites = routeLoader$(async ({ sharedMap, cookie, query }) => {
  const requestContext = buildServerRequestContext({ sharedMap, cookie });
  const meetingId = query.get("invitesMeetingId");

  if (!meetingId) {
    return emptyInvitesPage;
  }

  try {
    return await fetchInvites(requestContext, meetingId);
  } catch {
    return emptyInvitesPage;
  }
});

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
      return mapRouteActionError(error, InviteServiceError, fail, "INVITE_UNKNOWN");
    }
  },
  zod$(createInviteSchema.extend({ meetingId: z.string().min(1, "meetingId обязателен") })),
);

export const useRevokeInvite = routeAction$(
  async (data, { sharedMap, cookie, fail }) => {
    const requestContext = await buildMutationRequestContext({ sharedMap, cookie });

    try {
      await revokeInvite(requestContext, data.meetingId, data.inviteId);
      return { success: true as const };
    } catch (error) {
      return mapRouteActionError(error, InviteServiceError, fail, "INVITE_UNKNOWN");
    }
  },
  zod$(z.object({ meetingId: z.string().min(1), inviteId: z.string().min(1) })),
);

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
      return mapRouteActionError(error, MeetingServiceError, fail, "MEETING_UNKNOWN");
    }
  },
  zod$(createMeetingSchema.and(z.object({ roomId: z.string().min(1, "roomId обязателен") }))),
);

export const useUpdateMeeting = routeAction$(
  async (data, { sharedMap, cookie, fail }) => {
    const requestContext = await buildMutationRequestContext({ sharedMap, cookie });

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
      return mapRouteActionError(error, MeetingServiceError, fail, "MEETING_UNKNOWN");
    }
  },
  zod$(updateMeetingSchema.and(z.object({ meetingId: z.string().min(1, "meetingId обязателен") }))),
);

export const useCancelMeeting = routeAction$(
  async (data, { sharedMap, cookie, fail }) => {
    const requestContext = await buildMutationRequestContext({ sharedMap, cookie });

    try {
      const meeting = await cancelMeeting(requestContext, data.meetingId);
      return { success: true as const, meeting };
    } catch (error) {
      return mapRouteActionError(error, MeetingServiceError, fail, "MEETING_UNKNOWN");
    }
  },
  zod$(z.object({ meetingId: z.string().min(1, "meetingId обязателен") })),
);

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
      return mapRouteActionError(error, MeetingServiceError, fail, "MEETING_UNKNOWN");
    }
  },
  zod$(assignParticipantSchema.extend({ meetingId: z.string().min(1, "meetingId обязателен") })),
);

export const useBulkAssignParticipants = routeAction$(
  async (data, { sharedMap, cookie, fail }) => {
    const requestContext = await buildMutationRequestContext({ sharedMap, cookie });

    try {
      const assignments = await bulkAssignParticipants(requestContext, data.meetingId, {
        defaultRole: data.defaultRole,
        participants: data.subjectIds.map((subjectId) => ({ subjectId })),
      });
      return { success: true as const, assignments };
    } catch (error) {
      return mapRouteActionError(error, MeetingServiceError, fail, "MEETING_UNKNOWN");
    }
  },
  zod$(bulkAssignParticipantsSchema.extend({ meetingId: z.string().min(1, "meetingId обязателен") })),
);

export const useUpdateParticipantRole = routeAction$(
  async (data, { sharedMap, cookie, fail }) => {
    const requestContext = await buildMutationRequestContext({ sharedMap, cookie });

    try {
      const assignment = await updateParticipantRole(requestContext, data.meetingId, data.subjectId, {
        role: data.role,
      });
      return { success: true as const, assignment };
    } catch (error) {
      return mapRouteActionError(error, MeetingServiceError, fail, "MEETING_UNKNOWN");
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
    const requestContext = await buildMutationRequestContext({ sharedMap, cookie });

    try {
      await unassignParticipant(requestContext, data.meetingId, data.subjectId);
      return { success: true as const };
    } catch (error) {
      return mapRouteActionError(error, MeetingServiceError, fail, "MEETING_UNKNOWN");
    }
  },
  zod$(
    z.object({
      meetingId: z.string().min(1, "meetingId обязателен"),
      subjectId: z.string().min(1, "subjectId обязателен"),
    }),
  ),
);