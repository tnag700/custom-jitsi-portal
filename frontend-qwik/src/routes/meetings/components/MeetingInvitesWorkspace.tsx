import { component$, type QRL } from "@qwik.dev/core";
import type { Invite, InviteErrorPayload } from "~/lib/domains/invites";
import { InviteForm, InviteList } from "~/lib/domains/invites";
import type { Meeting } from "~/lib/domains/meetings";

interface MeetingInvitesWorkspaceProps {
  meeting: Meeting;
  invites: Invite[];
  totalInvites: number;
  showCreateForm: boolean;
  createAction: unknown;
  createRunning: boolean;
  error?: InviteErrorPayload;
  onClose$: QRL<() => void>;
  onCopyLink$: QRL<(invite: Invite) => void>;
  onRevoke$: QRL<(invite: Invite) => void>;
  onCreate$: QRL<() => void>;
  onCancelCreate$: QRL<() => void>;
}

export const MeetingInvitesWorkspace = component$<MeetingInvitesWorkspaceProps>(
  ({
    meeting,
    invites,
    totalInvites,
    showCreateForm,
    createAction,
    createRunning,
    error,
    onClose$,
    onCopyLink$,
    onRevoke$,
    onCreate$,
    onCancelCreate$,
  }) => (
    <section class="rounded-xl border border-border bg-surface p-4 sm:p-5">
      <div class="mb-4 flex flex-wrap items-start justify-between gap-3">
        <div>
          <p class="text-xs font-medium uppercase tracking-wide text-blue-600 dark:text-blue-300">
            Гостевой доступ
          </p>
          <h2 class="mt-1 text-lg font-semibold text-text">{meeting.title}</h2>
          <p class="mt-1 text-sm text-muted">
            Создавайте и отзывайте ссылки для внешних участников.
          </p>
        </div>
        <button
          type="button"
          class="rounded-lg border border-border px-3 py-2 text-sm font-medium text-text hover:bg-bg focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500"
          onClick$={onClose$}
        >
          Закрыть
        </button>
      </div>

      <InviteList
        invites={invites}
        totalElements={totalInvites}
        canCreate={meeting.allowGuests}
        onCopyLink$={onCopyLink$}
        onRevoke$={onRevoke$}
        onCreateClick$={onCreate$}
      />

      {!meeting.allowGuests && (
        <div class="mt-4 rounded-lg border border-amber-300 bg-amber-50 p-3 text-sm text-amber-900 dark:border-amber-800 dark:bg-amber-950 dark:text-amber-100">
          Гостевой доступ выключен. Включите его в настройках встречи, чтобы
          выпускать новые ссылки. Ранее созданные ссылки уже заблокированы
          сервером.
        </div>
      )}

      {showCreateForm && meeting.allowGuests && (
        <div class="mt-5 border-t border-border pt-5">
          <InviteForm
            meetingId={meeting.meetingId}
            action={createAction}
            isLoading={createRunning}
            error={error}
            onCancel$={onCancelCreate$}
          />
        </div>
      )}
    </section>
  ),
);
