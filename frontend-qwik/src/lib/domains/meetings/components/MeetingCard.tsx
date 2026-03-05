import { component$, type QRL } from "@qwik.dev/core";
import type { Meeting } from "../types";
import { formatDateTime } from "~/lib/shared";

interface MeetingCardProps {
  meeting: Meeting;
  onEdit$: QRL<(meeting: Meeting) => void>;
  onCancel$: QRL<(meeting: Meeting) => void>;
  onParticipants$: QRL<(meeting: Meeting) => void>;
  onInvites$: QRL<(meeting: Meeting) => void>;
}

export const MeetingCard = component$<MeetingCardProps>(
  ({ meeting, onEdit$, onCancel$, onParticipants$, onInvites$ }) => {
    const isScheduled = meeting.status === "scheduled";

    const statusClass =
      meeting.status === "scheduled"
        ? "bg-blue-100 text-blue-800 dark:bg-blue-900 dark:text-blue-200"
        : meeting.status === "canceled"
          ? "bg-gray-100 text-gray-700 dark:bg-gray-700 dark:text-gray-200"
          : "bg-green-100 text-green-800 dark:bg-green-900 dark:text-green-200";

    return (
      <article class="rounded border border-border bg-surface p-4 text-text">
        <div class="mb-2 flex items-start justify-between gap-2">
          <h3 class="text-lg font-semibold">{meeting.title}</h3>
          <span class={["inline-block rounded-full px-2 py-0.5 text-xs font-medium", statusClass]}>
            {meeting.status}
          </span>
        </div>

        {meeting.description && (
          <p class="mb-2 line-clamp-2 text-sm text-muted">{meeting.description}</p>
        )}

        <div class="mb-3 flex flex-wrap items-center gap-2 text-xs text-muted">
          <span class="rounded bg-bg px-2 py-0.5">{meeting.meetingType}</span>
          <span>{formatDateTime(meeting.startsAt)} - {formatDateTime(meeting.endsAt)}</span>
        </div>

        <div class="mb-3 flex flex-wrap gap-2 text-xs text-muted">
          <span class="rounded border border-border px-2 py-0.5">
            {meeting.allowGuests ? "Гости: да" : "Гости: нет"}
          </span>
          <span class="rounded border border-border px-2 py-0.5">
            {meeting.recordingEnabled ? "Запись: вкл" : "Запись: выкл"}
          </span>
        </div>

        <div class="flex flex-wrap gap-2">
          {isScheduled && (
            <button
              type="button"
              class="rounded border border-border px-3 py-1 text-sm hover:bg-bg focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500"
              aria-label={`Редактировать встречу ${meeting.title}`}
              onClick$={() => onEdit$(meeting)}
            >
              Редактировать
            </button>
          )}

          {isScheduled && (
            <button
              type="button"
              class="rounded border border-amber-300 px-3 py-1 text-sm text-amber-700 hover:bg-amber-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-amber-500 dark:border-amber-700 dark:text-amber-300 dark:hover:bg-amber-950"
              aria-label={`Отменить встречу ${meeting.title}`}
              onClick$={() => onCancel$(meeting)}
            >
              Отменить
            </button>
          )}

          <button
            type="button"
            class="rounded border border-border px-3 py-1 text-sm hover:bg-bg focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500"
            aria-label={`Участники встречи ${meeting.title}`}
            onClick$={() => onParticipants$(meeting)}
          >
            Участники
          </button>

          <button
            type="button"
            class="rounded border border-border px-3 py-1 text-sm hover:bg-bg focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500"
            aria-label={`Инвайты встречи ${meeting.title}`}
            onClick$={() => onInvites$(meeting)}
          >
            Инвайты
          </button>
        </div>
      </article>
    );
  },
);
