import { $, component$, useSignal, useTask$ } from "@qwik.dev/core";
import { Form } from "@qwik.dev/router";
import { type Room, type RoomErrorPayload, RoomForm, RoomList } from "~/lib/domains/rooms";
import { ApiErrorAlert, AppDialog, AppToast, useAppToast } from "~/lib/shared";
import {
  useCloseRoom,
  useCreateRoom,
  useDeleteRoom,
  useRooms,
  useUpdateRoom,
} from "./route-handlers";

export default component$(() => {
  const roomsData = useRooms();
  const createAction = useCreateRoom();
  const updateAction = useUpdateRoom();
  const closeAction = useCloseRoom();
  const deleteAction = useDeleteRoom();

  const showCreateForm = useSignal(false);
  const showEditForm = useSignal(false);
  const editingRoom = useSignal<Room | null>(null);
  const confirmClose = useSignal<Room | null>(null);
  const confirmDelete = useSignal<Room | null>(null);
  const showCloseDialog = useSignal(false);
  const showDeleteDialog = useSignal(false);
  const { toast, showToast$, clearToast$ } = useAppToast();

  const openOnNextFrame$ = $((signal: { value: boolean }) => {
    if (typeof window === "undefined") {
      signal.value = true;
      return;
    }

    window.requestAnimationFrame(() => {
      signal.value = true;
    });
  });

  const handleEdit$ = $((room: Room) => {
    editingRoom.value = room;
    void openOnNextFrame$(showEditForm);
  });

  const handleCloseClick$ = $((room: Room) => {
    confirmClose.value = room;
    void openOnNextFrame$(showCloseDialog);
  });

  const handleDeleteClick$ = $((room: Room) => {
    confirmDelete.value = room;
    void openOnNextFrame$(showDeleteDialog);
  });

  const handleCreateClick$ = $(() => {
    showCreateForm.value = true;
  });

  useTask$(async ({ track }) => {
    const result = track(() => createAction.value);
    if (result && "success" in result && result.success) {
      showCreateForm.value = false;
      await showToast$({ message: "Комната создана", tone: "success" });
    }
  });

  useTask$(async ({ track }) => {
    const result = track(() => updateAction.value);
    if (result && "success" in result && result.success) {
      showEditForm.value = false;
      await showToast$({ message: "Изменения комнаты сохранены", tone: "success" });
    }
  });

  useTask$(({ track }) => {
    const result = track(() => closeAction.value);
    if (result && "success" in result && result.success) {
      showCloseDialog.value = false;
    }
  });

  useTask$(async ({ track }) => {
    const result = track(() => closeAction.value);
    if (result && "success" in result && result.success) {
      await showToast$({ message: "Комната закрыта", tone: "info" });
    }
  });

  useTask$(({ track }) => {
    const result = track(() => deleteAction.value);
    if (result && "success" in result && result.success) {
      showDeleteDialog.value = false;
    }
  });

  useTask$(async ({ track }) => {
    const result = track(() => deleteAction.value);
    if (result && "success" in result && result.success) {
      await showToast$({ message: "Комната удалена", tone: "warning" });
    }
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
    : null;

  const deleteErrorMessage = deleteError
    ? deleteError.errorCode === "ROOM_HAS_ACTIVE_MEETINGS"
      ? "Нельзя удалить комнату с активными встречами"
      : deleteError.detail
    : null;

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

      <RoomForm
        action={createAction}
        configSets={configSets}
        isLoading={createAction.isRunning}
        error={createError}
        isOpen={showCreateForm}
      />

      <RoomForm
        action={updateAction}
        room={editingRoom.value ?? undefined}
        configSets={configSets}
        isLoading={updateAction.isRunning}
        error={updateError}
        isOpen={showEditForm}
      />

      <AppDialog
        title="Закрыть комнату?"
        description={
          confirmClose.value
            ? `Комната «${confirmClose.value.name}» станет недоступной для новых встреч.`
            : "Комната станет недоступной для новых встреч."
        }
        maxWidth="max-w-sm"
        showTrigger={false}
        closeLabel="Отмена"
        bind:show={showCloseDialog}
      >
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

        <p class="text-sm text-muted">
          После закрытия создать новую встречу в этой комнате будет нельзя, пока вы не откроете другой сценарий работы.
        </p>

        <Form q:slot="actions" action={closeAction}>
          <input type="hidden" name="roomId" value={confirmClose.value?.roomId ?? ""} />
          <button
            type="submit"
            class="rounded bg-yellow-600 px-4 py-2 text-sm text-white hover:bg-yellow-700 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-yellow-500"
            disabled={closeAction.isRunning || !confirmClose.value}
          >
            {closeAction.isRunning ? "Закрытие..." : "Закрыть комнату"}
          </button>
        </Form>
      </AppDialog>

      <AppDialog
        title="Удалить комнату?"
        description={
          confirmDelete.value
            ? `Комната «${confirmDelete.value.name}» будет удалена без возможности восстановления.`
            : "Комната будет удалена без возможности восстановления."
        }
        maxWidth="max-w-sm"
        showTrigger={false}
        closeLabel="Отмена"
        bind:show={showDeleteDialog}
      >
        <p class="mb-4 text-sm font-semibold text-danger">Все данные комнаты будут потеряны.</p>

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

        <Form q:slot="actions" action={deleteAction}>
          <input type="hidden" name="roomId" value={confirmDelete.value?.roomId ?? ""} />
          <button
            type="submit"
            class="rounded bg-red-600 px-4 py-2 text-sm text-white hover:bg-red-700 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-red-500"
            disabled={deleteAction.isRunning || !confirmDelete.value}
          >
            {deleteAction.isRunning ? "Удаление..." : "Удалить комнату"}
          </button>
        </Form>
      </AppDialog>

      <AppToast toast={toast.value} onDismiss$={clearToast$} />
    </div>
  );
});