/* eslint-disable qwik/loader-location */

import { routeLoader$ } from "@qwik.dev/router";
import type { SafeUserProfile } from "~/lib/domains/auth";
import {
  fetchMeetings,
  fetchParticipants,
  searchUsers,
} from "~/lib/domains/meetings";
import { fetchInvites } from "~/lib/domains/invites";
import { fetchRooms } from "~/lib/domains/rooms";
import { buildServerRequestContext } from "~/lib/shared/routes/server-handlers";

const emptyPage = {
  content: [],
  page: 0,
  pageSize: 20,
  totalElements: 0,
  totalPages: 0,
};

const UUID_LIKE_PATTERN =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

function isUuidLike(value: string | null): value is string {
  return value !== null && UUID_LIKE_PATTERN.test(value);
}

export const useActiveRooms = routeLoader$(async ({ sharedMap, cookie }) => {
  const user = sharedMap.get("user") as SafeUserProfile;
  const requestContext = buildServerRequestContext({ sharedMap, cookie });

  const rooms = await fetchRooms(requestContext, user.tenant);
  return {
    ...rooms,
    content: rooms.content.filter((room) => room.status === "active"),
  };
});

export const useMeetings = routeLoader$(
  async ({ sharedMap, cookie, query }) => {
    const requestContext = buildServerRequestContext({ sharedMap, cookie });
    const roomId = query.get("roomId");

    if (!roomId) {
      return emptyPage;
    }

    return fetchMeetings(requestContext, roomId);
  },
);

export const useParticipants = routeLoader$(
  async ({ sharedMap, cookie, query }) => {
    const requestContext = buildServerRequestContext({ sharedMap, cookie });
    const meetingId = query.get("meetingId");

    if (!isUuidLike(meetingId)) {
      return [];
    }

    return fetchParticipants(requestContext, meetingId);
  },
);

export const useAssignableUsers = routeLoader$(
  async ({ sharedMap, cookie, query }) => {
    const user = sharedMap.get("user") as SafeUserProfile;
    const requestContext = buildServerRequestContext({ sharedMap, cookie });
    const meetingId = query.get("meetingId");

    if (!isUuidLike(meetingId)) {
      return [];
    }

    const participantQuery = query.get("participantQuery") ?? undefined;
    const participantOrganization =
      query.get("participantOrganization") ?? undefined;

    return searchUsers(
      requestContext,
      user.tenant,
      participantQuery,
      participantOrganization,
    );
  },
);

export const useInvites = routeLoader$(async ({ sharedMap, cookie, query }) => {
  const requestContext = buildServerRequestContext({ sharedMap, cookie });
  const meetingId = query.get("invitesMeetingId");

  if (!meetingId) {
    return emptyPage;
  }

  return fetchInvites(requestContext, meetingId);
});
