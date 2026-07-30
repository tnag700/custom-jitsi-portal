import { component$, type Signal } from "@qwik.dev/core";
import { Form } from "@qwik.dev/router";
import type { Invite, InviteErrorPayload } from "~/lib/domains/invites";
import type { Meeting, MeetingErrorPayload } from "~/lib/domains/meetings";
import { ApiErrorAlert, AppDialog } from "~/lib/shared";

interface MeetingConfirmationDialogsProps {
  confirmingCancel: Signal<Meeting | null>;
  confirmingRevokeInvite: Signal<Invite | null>;
  selectedInviteMeeting: Meeting | null;
  showCancelDialog: Signal<boolean>;
  showRevokeInviteDialog: Signal<boolean>;
  cancelAction: unknown;
  revokeInviteAction: unknown;
  cancelRunning: boolean;
  revokeRunning: boolean;
  cancelError?: MeetingErrorPayload;
  inviteError?: InviteErrorPayload;
}

function getCancelMessage(error: MeetingErrorPayload): string {
  if (error.errorCode === "MEETING_FINALIZED") {
    return "Встреча уже завершена или отменена";
  }
  if (error.errorCode === "INVALID_SCHEDULE") {
    return "Некорректное расписание";
  }
  return error.detail;
}

function getInviteMessage(error: InviteErrorPayload): string {
  if (error.errorCode === "INVITE_NOT_FOUND") {
    return "Инвайт не найден";
  }
  if (error.errorCode === "INVITE_ALREADY_REVOKED") {
    return "Инвайт уже отозван";
  }
  return error.detail;
}

export const MeetingConfirmationDialogs =
  component$<MeetingConfirmationDialogsProps>(
    ({
      confirmingCancel,
      confirmingRevokeInvite,
      selectedInviteMeeting,
      showCancelDialog,
      showRevokeInviteDialog,
      cancelAction,
      revokeInviteAction,
      cancelRunning,
      revokeRunning,
      cancelError,
      inviteError,
    }) => (
      <>
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
                message={getCancelMessage(cancelError)}
                errorCode={cancelError.errorCode}
                traceId={cancelError.traceId}
              />
            </div>
          )}

          <Form q:slot="actions" action={cancelAction as never}>
            <input
              type="hidden"
              name="meetingId"
              value={confirmingCancel.value?.meetingId ?? ""}
            />
            <button
              type="submit"
              class="rounded-lg bg-amber-600 px-4 py-2 text-sm font-medium text-white hover:bg-amber-700 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-amber-500 disabled:opacity-50"
              disabled={cancelRunning || !confirmingCancel.value}
            >
              {cancelRunning ? "Отменяем..." : "Отменить встречу"}
            </button>
          </Form>
        </AppDialog>

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
                  message={getInviteMessage(inviteError)}
                  errorCode={inviteError.errorCode}
                  traceId={inviteError.traceId}
                />
              </div>
            )}

            <Form q:slot="actions" action={revokeInviteAction as never}>
              <input
                type="hidden"
                name="meetingId"
                value={selectedInviteMeeting.meetingId}
              />
              <input
                type="hidden"
                name="inviteId"
                value={confirmingRevokeInvite.value?.id ?? ""}
              />
              <button
                type="submit"
                class="rounded-lg bg-amber-600 px-4 py-2 text-sm font-medium text-white hover:bg-amber-700 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-amber-500 disabled:opacity-50"
                disabled={revokeRunning || !confirmingRevokeInvite.value}
              >
                {revokeRunning ? "Удаляем..." : "Удалить ссылку"}
              </button>
            </Form>
          </AppDialog>
        )}
      </>
    ),
  );
