/* eslint-disable qwik/loader-location */

import { routeLoader$ } from "@qwik.dev/router";
import type { SafeUserProfile } from "~/lib/domains/auth";
import {
  fetchMeetings,
  fetchMeeting,
  fetchParticipants,
  searchUsers,
} from "~/lib/domains/meetings";
import { fetchInvites } from "~/lib/domains/invites";
import { fetchRoom, fetchRooms } from "~/lib/domains/rooms";
import { buildServerRequestContext } from "~/lib/shared/routes/server-handlers";
import { readPage } from "~/lib/shared/routes/page-query";
import type { RoomMeetingPreview } from "./components/MeetingsOverview";

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

async function selectedMeeting(
  query: URLSearchParams,
  parameter: string,
  requestContext: ReturnType<typeof buildServerRequestContext>,
) {
  const id = query.get(parameter);
  return isUuidLike(id) ? fetchMeeting(requestContext, id) : null;
}

export const useSelectedMeeting = routeLoader$(
  async ({ sharedMap, cookie, query }) =>
    selectedMeeting(
      query,
      "meetingId",
      buildServerRequestContext({ sharedMap, cookie }),
    ),
);

export const useSelectedInviteMeeting = routeLoader$(
  async ({ sharedMap, cookie, query }) =>
    selectedMeeting(
      query,
      "invitesMeetingId",
      buildServerRequestContext({ sharedMap, cookie }),
    ),
);

export const useWorkspaceRooms = routeLoader$(
  async ({ sharedMap, cookie, query }) => {
    const user = sharedMap.get("user") as SafeUserProfile;
    const requestContext = buildServerRequestContext({ sharedMap, cookie });

    const rooms = await fetchRooms(
      requestContext,
      user.tenant,
      readPage(query, "roomsPage"),
      6,
    );
    const selectedRoomId = query?.get("roomId");
    const content = [...rooms.content];
    if (
      isUuidLike(selectedRoomId) &&
      !content.some((room) => room.roomId === selectedRoomId)
    ) {
      content.push(await fetchRoom(requestContext, selectedRoomId));
    }
    const previews: Record<string, RoomMeetingPreview> = {};
    // ponytail: at most six preview requests per page; add a batch endpoint if room density grows.
    await Promise.all(
      content
        .filter((room) => room.roomId !== selectedRoomId)
        .map(async (room) => {
          try {
            const page = await fetchMeetings(requestContext, room.roomId, 0, 3);
            previews[room.roomId] = {
              content: page.content,
              totalElements: page.totalElements,
            };
          } catch {
            previews[room.roomId] = { error: true };
          }
        }),
    );
    return {
      ...rooms,
      content,
      previews,
    };
  },
);

export const useMeetings = routeLoader$(
  async ({ sharedMap, cookie, query }) => {
    const requestContext = buildServerRequestContext({ sharedMap, cookie });
    const roomId = query.get("roomId");

    if (!roomId) {
      return emptyPage;
    }

    return fetchMeetings(
      requestContext,
      roomId,
      readPage(query, "meetingsPage"),
    );
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

  return fetchInvites(
    requestContext,
    meetingId,
    readPage(query, "invitesPage"),
  );
});
