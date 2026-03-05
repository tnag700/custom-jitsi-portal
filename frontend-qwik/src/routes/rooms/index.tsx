import { $, component$, useSignal } from "@qwik.dev/core";
import { routeAction$, routeLoader$, zod$, Form, z } from "@qwik.dev/router";
import type { SafeUserProfile } from "~/lib/domains/auth";
import { ApiErrorAlert } from "~/lib/shared";
import {
  fetchRooms,
  createRoom,
  updateRoom,
  closeRoom,
  deleteRoom,
  RoomServiceError,
  createRoomSchema,
  updateRoomSchema,
  RoomList,
  RoomForm,
  type Room,
  type RoomErrorPayload,
} from "~/lib/domains/rooms";

export const useRooms = routeLoader$(async ({ sharedMap, cookie }) => {
  const user = sharedMap.get("user") as SafeUserProfile;
  const apiUrl = sharedMap.get("apiUrl") as string;
  const sessionCookie = cookie.get("JSESSIONID")?.value ?? "";
  return fetchRooms(sessionCookie, apiUrl, user.tenant);
});

export const useCreateRoom = routeAction$(
  async (data, { sharedMap, cookie, fail }) => {
    const user = sharedMap.get("user") as SafeUserProfile;
    const apiUrl = sharedMap.get("apiUrl") as string;
    const sessionCookie = cookie.get("JSESSIONID")?.value ?? "";
    const csrfToken = cookie.get("XSRF-TOKEN")?.value ?? "";
    const idempotencyKey = crypto.randomUUID();
    try {
      const room = await createRoom(sessionCookie, apiUrl, csrfToken, idempotencyKey, {
        ...data,
        tenantId: user.tenant,
      });
      return { success: true as const, room };
    } catch (e) {
      if (e instanceof RoomServiceError) {
        return fail(400, { error: e.payload });
      }
      return fail(500, { error: { title: "Ошибка", detail: "Неизвестная ошибка", errorCode: "ROOM_UNKNOWN" } });
    }
  },
  zod$(createRoomSchema),
);

export const useUpdateRoom = routeAction$(
  async (data, { sharedMap, cookie, fail }) => {
    const apiUrl = sharedMap.get("apiUrl") as string;
    const sessionCookie = cookie.get("JSESSIONID")?.value ?? "";
    const csrfToken = cookie.get("XSRF-TOKEN")?.value ?? "";
    const idempotencyKey = crypto.randomUUID();
    const { roomId, ...updateData } = data;
    try {
      const room = await updateRoom(
        sessionCookie,
        apiUrl,
        csrfToken,
        idempotencyKey,
        roomId,
        updateData,
      );
      return { success: true as const, room };
    } catch (e) {
      if (e instanceof RoomServiceError) {
        return fail(400, { error: e.payload });
      }
      return fail(500, { error: { title: "Ошибка", detail: "Неизвестная ошибка", errorCode: "ROOM_UNKNOWN" } });
    }
  },
  zod$(updateRoomSchema.extend({ roomId: z.string().min(1, "roomId обязателен") })),
);

const roomIdSchema = z.object({ roomId: z.string().min(1, "roomId обязателен") });

export const useCloseRoom = routeAction$(async (data, { sharedMap, cookie, fail }) => {
  const apiUrl = sharedMap.get("apiUrl") as string;
  const sessionCookie = cookie.get("JSESSIONID")?.value ?? "";
  const csrfToken = cookie.get("XSRF-TOKEN")?.value ?? "";
  const idempotencyKey = crypto.randomUUID();
  try {
    const room = await closeRoom(sessionCookie, apiUrl, csrfToken, idempotencyKey, data.roomId);
    return { success: true as const, room };
  } catch (e) {
    if (e instanceof RoomServiceError) {
      return fail(400, { error: e.payload });
    }
    return fail(500, { error: { title: "Ошибка", detail: "Неизвестная ошибка", errorCode: "ROOM_UNKNOWN" } });
  }
}, zod$(roomIdSchema));

export const useDeleteRoom = routeAction$(async (data, { sharedMap, cookie, fail }) => {
  const apiUrl = sharedMap.get("apiUrl") as string;
  const sessionCookie = cookie.get("JSESSIONID")?.value ?? "";
  const csrfToken = cookie.get("XSRF-TOKEN")?.value ?? "";
  const idempotencyKey = crypto.randomUUID();
  try {
    await deleteRoom(sessionCookie, apiUrl, csrfToken, idempotencyKey, data.roomId);
    return { success: true as const };
  } catch (e) {
    if (e instanceof RoomServiceError) {
      return fail(400, { error: e.payload });
    }
    return fail(500, { error: { title: "Ошибка", detail: "Неизвестная ошибка", errorCode: "ROOM_UNKNOWN" } });
  }
}, zod$(roomIdSchema));

export default component$(() => {
  const roomsData = useRooms();
  const createAction = useCreateRoom();
  const updateAction = useUpdateRoom();
  const closeAction = useCloseRoom();
  const deleteAction = useDeleteRoom();

  const showCreateForm = useSignal(false);
  const editingRoom = useSignal<Room | null>(null);
  const confirmClose = useSignal<Room | null>(null);
  const confirmDelete = useSignal<Room | null>(null);
  const actionError = useSignal<string | null>(null);

  const handleEdit$ = $((room: Room) => {
    editingRoom.value = room;
  });

  const handleCloseClick$ = $((room: Room) => {
    confirmClose.value = room;
    actionError.value = null;
  });

  const handleDeleteClick$ = $((room: Room) => {
    confirmDelete.value = room;
    actionError.value = null;
  });

  const handleCreateClick$ = $(() => {
    showCreateForm.value = true;
  });

  const handleCancelCreate$ = $(() => {
    showCreateForm.value = false;
  });

  const handleCancelEdit$ = $(() => {
    editingRoom.value = null;
  });

  const configSets = ["default"];

  const createError: RoomErrorPayload | undefined =
    createAction.value && "error" in createAction.value
      ? (createAction.value as { error: RoomErrorPayload }).error
      : undefined;

  const updateError: RoomErrorPayload | undefined =
    updateAction.value && "error" in updateAction.value
      ? (updateAction.value as { error: RoomErrorPayload }).error
      : undefined;

  const closeError: RoomErrorPayload | undefined =
    closeAction.value && "error" in closeAction.value
      ? (closeAction.value as { error: RoomErrorPayload }).error
      : undefined;

  const deleteError: RoomErrorPayload | undefined =
    deleteAction.value && "error" in deleteAction.value
      ? (deleteAction.value as { error: RoomErrorPayload }).error
      : undefined;

  const closeErrorMessage = closeError
    ? closeError.errorCode === "ROOM_ALREADY_CLOSED"
      ? "Комната уже закрыта"
      : closeError.errorCode === "ROOM_HAS_ACTIVE_MEETINGS"
        ? "Сначала отмените или завершите встречи"
        : closeError.detail
    : actionError.value;

  const deleteErrorMessage = deleteError
    ? deleteError.errorCode === "ROOM_HAS_ACTIVE_MEETINGS"
      ? "Нельзя удалить комнату с активными встречами"
      : deleteError.detail
    : actionError.value;

  return (
    <div>
      <RoomList
        rooms={roomsData.value.content}
        totalElements={roomsData.value.totalElements}
        onEdit$={handleEdit$}
        onClose$={handleCloseClick$}
        onDelete$={handleDeleteClick$}
        onCreateClick$={handleCreateClick$}
      />

      {showCreateForm.value && (
        <RoomForm
          action={createAction}
          configSets={configSets}
          isLoading={createAction.isRunning}
          error={createError}
          onCancel$={handleCancelCreate$}
        />
      )}

      {editingRoom.value && (
        <RoomForm
          action={updateAction}
          room={editingRoom.value}
          configSets={configSets}
          isLoading={updateAction.isRunning}
          error={updateError}
          onCancel$={handleCancelEdit$}
        />
      )}

      {confirmClose.value && (
        <div class="fixed inset-0 z-50 flex items-center justify-center bg-black/50">
          <div class="w-full max-w-sm rounded border border-border bg-surface p-6">
            <h2 class="mb-2 text-lg font-semibold text-text">Закрыть комнату?</h2>
            <p class="mb-4 text-sm text-muted">
              Комната «{confirmClose.value.name}» будет закрыта. Новые встречи в ней
              создать будет нельзя.
            </p>
            {closeErrorMessage && (
              <div class="mb-3" role="alert">
                <ApiErrorAlert
                  title="Ошибка закрытия комнаты"
                  message={closeErrorMessage}
                  errorCode={closeError?.errorCode}
                  traceId={closeError?.traceId}
                />
              </div>
            )}
            <div class="flex justify-end gap-3">
              <button
                type="button"
                class="rounded border border-border px-4 py-2 text-sm text-text hover:bg-bg focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500"
                onClick$={() => {
                  confirmClose.value = null;
                  actionError.value = null;
                }}
              >
                Отмена
              </button>
              <Form action={closeAction}>
                <input type="hidden" name="roomId" value={confirmClose.value.roomId} />
                <button
                  type="submit"
                  class="rounded bg-yellow-600 px-4 py-2 text-sm text-white hover:bg-yellow-700 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-yellow-500"
                  disabled={closeAction.isRunning}
                >
                  {closeAction.isRunning ? "Закрытие..." : "Закрыть"}
                </button>
              </Form>
            </div>
          </div>
        </div>
      )}

      {confirmDelete.value && (
        <div class="fixed inset-0 z-50 flex items-center justify-center bg-black/50">
          <div class="w-full max-w-sm rounded border border-border bg-surface p-6">
            <h2 class="mb-2 text-lg font-semibold text-text">Удалить комнату?</h2>
            <p class="mb-2 text-sm text-muted">
              Комната «{confirmDelete.value.name}» будет удалена. Это действие необратимо.
            </p>
            <p class="mb-4 text-xs font-semibold text-red-600">
              ⚠️ Все данные комнаты будут потеряны.
            </p>
            {deleteErrorMessage && (
              <div class="mb-3" role="alert">
                <ApiErrorAlert
                  title="Ошибка удаления комнаты"
                  message={deleteErrorMessage}
                  errorCode={deleteError?.errorCode}
                  traceId={deleteError?.traceId}
                />
              </div>
            )}
            <div class="flex justify-end gap-3">
              <button
                type="button"
                class="rounded border border-border px-4 py-2 text-sm text-text hover:bg-bg focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500"
                onClick$={() => {
                  confirmDelete.value = null;
                  actionError.value = null;
                }}
              >
                Отмена
              </button>
              <Form action={deleteAction}>
                <input type="hidden" name="roomId" value={confirmDelete.value.roomId} />
                <button
                  type="submit"
                  class="rounded bg-red-600 px-4 py-2 text-sm text-white hover:bg-red-700 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-red-500"
                  disabled={deleteAction.isRunning}
                >
                  {deleteAction.isRunning ? "Удаление..." : "Удалить"}
                </button>
              </Form>
            </div>
          </div>
        </div>
      )}
    </div>
  );
});
