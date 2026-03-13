import { $, component$, useSignal, useTask$ } from "@qwik.dev/core";
import { Form, Link, useLocation, useNavigate } from "@qwik.dev/router";
import { type Invite, type InviteErrorPayload, InviteForm, InviteList } from "~/lib/domains/invites";
import {
  type Meeting,
  type MeetingErrorPayload,
  MeetingForm,
  MeetingList,
  ParticipantPanel,
} from "~/lib/domains/meetings";
import { ApiErrorAlert, AppDialog, AppToast, useAppToast } from "~/lib/shared";
import {
  useActiveRooms,
  useAssignParticipant,
  useBulkAssignParticipants,
  useCancelMeeting,
  useAssignableUsers,
  useCreateInvite,
  useCreateMeeting,
  useInvites,
  useMeetings,
  useParticipants,
  useRevokeInvite,
  useUnassignParticipant,
  useUpdateMeeting,
  useUpdateParticipantRole,
} from "./route-handlers";

export default component$(() => {
  const nav = useNavigate();
  const loc = useLocation();

  const roomsData = useActiveRooms();
  const meetingsData = useMeetings();
  const participantsData = useParticipants();
  const assignableUsersData = useAssignableUsers();
  const invitesData = useInvites();

  const createAction = useCreateMeeting();
  const updateAction = useUpdateMeeting();
  const cancelAction = useCancelMeeting();
  const assignAction = useAssignParticipant();
  const bulkAssignAction = useBulkAssignParticipants();
  const updateRoleAction = useUpdateParticipantRole();
  const unassignAction = useUnassignParticipant();
  const createInviteAction = useCreateInvite();
  const revokeInviteAction = useRevokeInvite();

  const showCreateForm = useSignal(false);
  const showEditForm = useSignal(false);
  const editingMeeting = useSignal<Meeting | null>(null);
  const confirmingCancel = useSignal<Meeting | null>(null);
  const showInviteForm = useSignal(false);
  const confirmingRevokeInvite = useSignal<Invite | null>(null);
  const showCancelDialog = useSignal(false);
  const showRevokeInviteDialog = useSignal(false);
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

  const selectedRoomId = loc.url.searchParams.get("roomId") ?? "";
  const selectedMeetingId = loc.url.searchParams.get("meetingId") ?? "";
  const selectedInviteMeetingId = loc.url.searchParams.get("invitesMeetingId") ?? "";

  const selectedMeeting =
    meetingsData.value.content.find((meeting) => meeting.meetingId === selectedMeetingId) ?? null;

  const selectedInviteMeeting =
    meetingsData.value.content.find((meeting) => meeting.meetingId === selectedInviteMeetingId) ?? null;

  const createError: MeetingErrorPayload | undefined =
    createAction.value && "error" in createAction.value
      ? (createAction.value as { error: MeetingErrorPayload }).error
      : undefined;

  const updateError: MeetingErrorPayload | undefined =
    updateAction.value && "error" in updateAction.value
      ? (updateAction.value as { error: MeetingErrorPayload }).error
      : undefined;

  const cancelError: MeetingErrorPayload | undefined =
    cancelAction.value && "error" in cancelAction.value
      ? (cancelAction.value as { error: MeetingErrorPayload }).error
      : undefined;

  const participantError: MeetingErrorPayload | undefined =
    (assignAction.value && "error" in assignAction.value
      ? (assignAction.value as { error: MeetingErrorPayload }).error
      : undefined) ??
    (bulkAssignAction.value && "error" in bulkAssignAction.value
      ? (bulkAssignAction.value as { error: MeetingErrorPayload }).error
      : undefined) ??
    (updateRoleAction.value && "error" in updateRoleAction.value
      ? (updateRoleAction.value as { error: MeetingErrorPayload }).error
      : undefined) ??
    (unassignAction.value && "error" in unassignAction.value
      ? (unassignAction.value as { error: MeetingErrorPayload }).error
      : undefined);

  const inviteError: InviteErrorPayload | undefined =
    (createInviteAction.value && "error" in createInviteAction.value
      ? (createInviteAction.value as { error: InviteErrorPayload }).error
      : undefined) ??
    (revokeInviteAction.value && "error" in revokeInviteAction.value
      ? (revokeInviteAction.value as { error: InviteErrorPayload }).error
      : undefined);

  const navigateWithRoom$ = $((roomId: string) => {
    void nav(`/meetings?roomId=${encodeURIComponent(roomId)}`);
  });

  const handleParticipants$ = $((meeting: Meeting) => {
    void nav(`/meetings?roomId=${encodeURIComponent(meeting.roomId)}&meetingId=${encodeURIComponent(meeting.meetingId)}`);
  });

  const closeParticipants$ = $(() => {
    if (selectedRoomId) {
      void nav(`/meetings?roomId=${encodeURIComponent(selectedRoomId)}`);
      return;
    }
    void nav("/meetings");
  });

  const handleInvites$ = $((meeting: Meeting) => {
    void nav(
      `/meetings?roomId=${encodeURIComponent(meeting.roomId)}&invitesMeetingId=${encodeURIComponent(meeting.meetingId)}`,
    );
  });

  const closeInvites$ = $(() => {
    if (selectedRoomId) {
      void nav(`/meetings?roomId=${encodeURIComponent(selectedRoomId)}`);
      return;
    }
    void nav("/meetings");
  });

  const copyInviteLink$ = $((invite: Invite) => {
    if (typeof window === "undefined") return;
    const url = `${window.location.origin}/invite/${invite.token}/`;
    void navigator.clipboard.writeText(url);
    void showToast$({ message: "Ссылка скопирована", tone: "info" });
  });

  useTask$(async ({ track }) => {
    const createInviteResult = track(() => createInviteAction.value);
    if (!createInviteResult || !("success" in createInviteResult) || !createInviteResult.success) {
      return;
    }

    showInviteForm.value = false;
    await showToast$({ message: "Инвайт создан", tone: "success" });
  });

  useTask$(async ({ track }) => {
    const createMeetingResult = track(() => createAction.value);
    if (!createMeetingResult || !("success" in createMeetingResult) || !createMeetingResult.success) {
      return;
    }

    showCreateForm.value = false;
    await showToast$({ message: "Встреча создана", tone: "success" });
  });

  useTask$(async ({ track }) => {
    const updateMeetingResult = track(() => updateAction.value);
    if (!updateMeetingResult || !("success" in updateMeetingResult) || !updateMeetingResult.success) {
      return;
    }

    showEditForm.value = false;
    await showToast$({ message: "Изменения встречи сохранены", tone: "success" });
  });

  useTask$(async ({ track }) => {
    const result = track(() => cancelAction.value);
    if (result && "success" in result && result.success) {
      showCancelDialog.value = false;
      await showToast$({ message: "Встреча отменена", tone: "info" });
    }
  });

  useTask$(async ({ track }) => {
    const result = track(() => revokeInviteAction.value);
    if (result && "success" in result && result.success) {
      showRevokeInviteDialog.value = false;
      await showToast$({ message: "Ссылка удалена из активного списка", tone: "warning" });
    }
  });

  return (
    <div class="space-y-6">
      <section class="rounded border border-border bg-surface p-4">
        <h2 class="mb-3 text-sm font-semibold text-text">Выберите комнату</h2>

        {roomsData.value.content.length === 0 ? (
          <p class="text-sm text-muted">Нет активных комнат</p>
        ) : (
          <>
            <label class="sr-only" for="room-selector">Комната</label>
            <select
              id="room-selector"
              class="w-full rounded border border-border bg-bg px-3 py-2 text-text focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500"
              value={selectedRoomId}
              onChange$={(_, el) => navigateWithRoom$(el.value)}
            >
              <option value="">-- Выберите комнату --</option>
              {roomsData.value.content.map((room) => (
                <option key={room.roomId} value={room.roomId}>
                  {room.name}
                </option>
              ))}
            </select>

            <div class="mt-3 flex flex-wrap gap-2">
              {roomsData.value.content.map((room) => (
                <Link
                  key={room.roomId}
                  href={`/meetings?roomId=${encodeURIComponent(room.roomId)}`}
                  class={[
                    "rounded border px-2 py-1 text-xs",
                    selectedRoomId === room.roomId
                      ? "border-blue-500 bg-blue-50 text-blue-700 dark:bg-blue-950 dark:text-blue-200"
                      : "border-border text-muted hover:bg-bg",
                  ]}
                >
                  {room.name}
                </Link>
              ))}
            </div>
          </>
        )}
      </section>

      {selectedRoomId ? (
        <MeetingList
          meetings={meetingsData.value.content}
          totalElements={meetingsData.value.totalElements}
          onEdit$={$((meeting: Meeting) => {
            editingMeeting.value = meeting;
            void openOnNextFrame$(showEditForm);
          })}
          onCancel$={$((meeting: Meeting) => {
            confirmingCancel.value = meeting;
            void openOnNextFrame$(showCancelDialog);
          })}
          onParticipants$={handleParticipants$}
          onInvites$={handleInvites$}
          onCreateClick$={$(() => {
            showCreateForm.value = true;
          })}
        />
      ) : (
        <div class="rounded border border-dashed border-border p-8 text-center text-sm text-muted">
          Выберите комнату, чтобы загрузить встречи
        </div>
      )}

      {selectedRoomId && (
        <MeetingForm
          action={createAction}
          roomId={selectedRoomId}
          isLoading={createAction.isRunning}
          error={createError}
          isOpen={showCreateForm}
        />
      )}

      <MeetingForm
        action={updateAction}
        roomId={editingMeeting.value?.roomId ?? selectedRoomId}
        meeting={editingMeeting.value ?? undefined}
        isLoading={updateAction.isRunning}
        error={updateError}
        isOpen={showEditForm}
      />

      <AppDialog
        title="Отменить встречу?"
        description={
          confirmingCancel.value
            ? `Встреча «${confirmingCancel.value.title}» будет переведена в статус «Отменена».`
            : "Встреча будет переведена в статус «Отменена»."
        }
        maxWidth="max-w-sm"
        showTrigger={false}
        closeLabel="Оставить как есть"
        bind:show={showCancelDialog}
      >
        {cancelError && (
          <div class="mb-3" role="alert">
            <ApiErrorAlert
              title="Ошибка отмены встречи"
              message={
                cancelError.errorCode === "MEETING_FINALIZED"
                  ? "Встреча уже завершена или отменена"
                  : cancelError.errorCode === "INVALID_SCHEDULE"
                    ? "Некорректное расписание"
                    : cancelError.detail
              }
              errorCode={cancelError.errorCode}
              traceId={cancelError.traceId}
            />
          </div>
        )}

        <Form q:slot="actions" action={cancelAction}>
          <input type="hidden" name="meetingId" value={confirmingCancel.value?.meetingId ?? ""} />
          <button
            type="submit"
            class="rounded bg-amber-600 px-4 py-2 text-sm text-white hover:bg-amber-700 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-amber-500"
            disabled={cancelAction.isRunning || !confirmingCancel.value}
          >
            {cancelAction.isRunning ? "Отменяем..." : "Отменить встречу"}
          </button>
        </Form>
      </AppDialog>

      {selectedMeeting && (
        <ParticipantPanel
          meeting={selectedMeeting}
          participants={participantsData.value}
          assignAction={assignAction}
          assignableUsers={assignableUsersData.value}
          bulkAssignAction={bulkAssignAction}
          updateRoleAction={updateRoleAction}
          unassignAction={unassignAction}
          error={participantError}
          onClose$={closeParticipants$}
        />
      )}

      {selectedInviteMeeting && (
        <div class="rounded border border-border bg-surface p-4">
          <div class="mb-3 flex items-center justify-between gap-2">
            <p class="text-sm text-muted">
              Управление инвайтами для встречи:{" "}
              <span class="font-semibold text-text">{selectedInviteMeeting.title}</span>
            </p>
            <button
              type="button"
              class="rounded border border-border px-3 py-1 text-sm text-text hover:bg-bg focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500"
              onClick$={closeInvites$}
            >
              Закрыть
            </button>
          </div>

          <InviteList
            invites={invitesData.value.content}
            totalElements={invitesData.value.totalElements}
            onCopyLink$={copyInviteLink$}
            onRevoke$={$((invite: Invite) => {
              confirmingRevokeInvite.value = invite;
              void openOnNextFrame$(showRevokeInviteDialog);
            })}
            onCreateClick$={$(() => {
              showInviteForm.value = true;
            })}
          />
        </div>
      )}

      {showInviteForm.value && selectedInviteMeeting && (
        <InviteForm
          meetingId={selectedInviteMeeting.meetingId}
          action={createInviteAction}
          isLoading={createInviteAction.isRunning}
          error={inviteError}
          onCancel$={$(() => {
            showInviteForm.value = false;
          })}
        />
      )}

      {selectedInviteMeeting && (
        <AppDialog
          title="Удалить ссылку инвайта?"
          description="Ссылка перестанет работать для новых входов, но останется в истории и будет скрыта фильтром активных ссылок."
          maxWidth="max-w-sm"
          showTrigger={false}
          closeLabel="Оставить ссылку"
          bind:show={showRevokeInviteDialog}
        >
          {inviteError && (
            <div class="mb-3" role="alert">
              <ApiErrorAlert
                title="Ошибка отзыва инвайта"
                message={
                  inviteError.errorCode === "INVITE_NOT_FOUND"
                    ? "Инвайт не найден"
                    : inviteError.errorCode === "INVITE_ALREADY_REVOKED"
                      ? "Инвайт уже отозван"
                      : inviteError.detail
                }
                errorCode={inviteError.errorCode}
                traceId={inviteError.traceId}
              />
            </div>
          )}

          <Form q:slot="actions" action={revokeInviteAction}>
            <input type="hidden" name="meetingId" value={selectedInviteMeeting.meetingId} />
            <input type="hidden" name="inviteId" value={confirmingRevokeInvite.value?.id ?? ""} />
            <button
              type="submit"
              class="rounded bg-amber-600 px-4 py-2 text-sm text-white hover:bg-amber-700 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-amber-500"
              disabled={revokeInviteAction.isRunning || !confirmingRevokeInvite.value}
            >
              {revokeInviteAction.isRunning ? "Удаляем..." : "Удалить ссылку"}
            </button>
          </Form>
        </AppDialog>
      )}

      <AppToast toast={toast.value} onDismiss$={clearToast$} />
    </div>
  );
});