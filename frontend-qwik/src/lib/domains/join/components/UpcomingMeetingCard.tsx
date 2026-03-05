import { component$, type QRL } from "@qwik.dev/core";
import { formatDateTime } from "~/lib/shared";
import type { UpcomingMeetingCard as UpcomingMeetingCardType } from "../types";

interface UpcomingMeetingCardProps {
  card: UpcomingMeetingCardType;
  isJoining: boolean;
  disabled?: boolean;
  onJoin$: QRL<(meetingId: string) => void>;
}

export const UpcomingMeetingCard = component$<UpcomingMeetingCardProps>(
  ({ card, isJoining, disabled, onJoin$ }) => {
    const isAvailable = card.joinAvailability === "available";

    const badgeClass = isAvailable
      ? "bg-green-100 text-green-800 dark:bg-green-900 dark:text-green-200"
      : "bg-gray-100 text-gray-700 dark:bg-gray-700 dark:text-gray-200";

    const badgeLabel = isAvailable ? "Доступна" : "Запланирована";

    return (
      <article class="rounded border border-border bg-surface p-4 text-text">
        <div class="mb-2 flex items-start justify-between gap-2">
          <h3 class="text-base font-semibold">{card.title}</h3>
          <span
            class={[
              "inline-block shrink-0 rounded-full px-2 py-0.5 text-xs font-medium",
              badgeClass,
            ]}
          >
            {badgeLabel}
          </span>
        </div>

        <div class="mb-1 text-sm text-muted">
          Комната: {card.roomName}
        </div>
        <div class="mb-3 text-sm text-muted">
          Начало: {formatDateTime(card.startsAt)}
        </div>

        {isAvailable ? (
          <button
            type="button"
            disabled={isJoining || disabled}
            class="rounded bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500 disabled:opacity-50"
            aria-label={`Войти во встречу ${card.title}`}
            onClick$={() => onJoin$(card.meetingId)}
          >
            {isJoining ? (
              <span class="inline-flex items-center gap-2">
                <span class="h-4 w-4 animate-spin rounded-full border-2 border-white border-t-transparent" />
                Подключение…
              </span>
            ) : (
              "Войти"
            )}
          </button>
        ) : (
          <button
            type="button"
            disabled
            class="rounded border border-border px-4 py-2 text-sm text-muted opacity-60"
            aria-disabled="true"
            title="Встреча ещё не началась"
          >
            {formatDateTime(card.startsAt)}
          </button>
        )}
      </article>
    );
  },
);
