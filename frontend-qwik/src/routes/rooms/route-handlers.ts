/* eslint-disable qwik/loader-location */

import { routeAction$, routeLoader$, z, zod$ } from "@qwik.dev/router";
import type { SafeUserProfile } from "~/lib/domains/auth";
import {
  RoomServiceError,
  closeRoom,
  createRoom,
  createRoomSchema,
  deleteRoom,
  fetchRooms,
  updateRoom,
  updateRoomSchema,
} from "~/lib/domains/rooms";
import {
  buildMutationRequestContext,
  buildServerRequestContext,
  mapRouteActionError,
} from "~/lib/shared/routes/server-handlers";

export const useRooms = routeLoader$(async ({ sharedMap, cookie }) => {
  const user = sharedMap.get("user") as SafeUserProfile;
  const requestContext = buildServerRequestContext({ sharedMap, cookie });
  return fetchRooms(requestContext, user.tenant);
});

export const useCreateRoom = routeAction$(
  async (data, { sharedMap, cookie, fail }) => {
    const user = sharedMap.get("user") as SafeUserProfile;
    const requestContext = await buildMutationRequestContext({
      sharedMap,
      cookie,
    });

    try {
      const room = await createRoom(requestContext, {
        ...data,
        tenantId: user.tenant,
      });
      return { success: true as const, room };
    } catch (error) {
      return mapRouteActionError(error, RoomServiceError, fail, "ROOM_UNKNOWN");
    }
  },
  zod$(createRoomSchema),
);

export const useUpdateRoom = routeAction$(
  async (data, { sharedMap, cookie, fail }) => {
    const requestContext = await buildMutationRequestContext({
      sharedMap,
      cookie,
    });
    const { roomId, ...updateData } = data;

    try {
      const room = await updateRoom(requestContext, roomId, updateData);
      return { success: true as const, room };
    } catch (error) {
      return mapRouteActionError(error, RoomServiceError, fail, "ROOM_UNKNOWN");
    }
  },
  zod$(updateRoomSchema.extend({ roomId: z.string().min(1, "roomId обязателен") })),
);

const roomIdSchema = z.object({ roomId: z.string().min(1, "roomId обязателен") });

export const useCloseRoom = routeAction$(
  async (data, { sharedMap, cookie, fail }) => {
    const requestContext = await buildMutationRequestContext({
      sharedMap,
      cookie,
    });

    try {
      const room = await closeRoom(requestContext, data.roomId);
      return { success: true as const, room };
    } catch (error) {
      return mapRouteActionError(error, RoomServiceError, fail, "ROOM_UNKNOWN");
    }
  },
  zod$(roomIdSchema),
);

export const useDeleteRoom = routeAction$(
  async (data, { sharedMap, cookie, fail }) => {
    const requestContext = await buildMutationRequestContext({
      sharedMap,
      cookie,
    });

    try {
      await deleteRoom(requestContext, data.roomId);
      return { success: true as const };
    } catch (error) {
      return mapRouteActionError(error, RoomServiceError, fail, "ROOM_UNKNOWN");
    }
  },
  zod$(roomIdSchema),
);