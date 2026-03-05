import { component$, useComputed$, useSignal, type QRL } from "@qwik.dev/core";
import type { Meeting } from "../types";
import { MeetingCard } from "./MeetingCard";
import { applyMeetingListState } from "./meeting-list-state";

interface MeetingListProps {
  meetings: Meeting[];
  totalElements: number;
  onEdit$: QRL<(meeting: Meeting) => void>;
  onCancel$: QRL<(meeting: Meeting) => void>;
  onParticipants$: QRL<(meeting: Meeting) => void>;
  onInvites$: QRL<(meeting: Meeting) => void>;
  onCreateClick$: QRL<() => void>;
}

export const MeetingList = component$<MeetingListProps>(
  ({ meetings, totalElements, onEdit$, onCancel$, onParticipants$, onInvites$, onCreateClick$ }) => {
    const statusFilter = useSignal<"all" | "scheduled" | "canceled" | "ended">("all");
    const sortBy = useSignal<"startsAt" | "title">("startsAt");

    const filteredMeetings = useComputed$(() => {
      return applyMeetingListState(meetings, statusFilter.value, sortBy.value);
    });

    const hasSourceMeetings = meetings.length > 0;
    const hasFilteredMeetings = filteredMeetings.value.length > 0;

    return (
      <section>
        <div class="mb-6 flex flex-wrap items-center justify-between gap-3">
          <h1 class="text-2xl font-bold text-text">Встречи</h1>
          <button
            type="button"
            class="rounded bg-blue-600 px-4 py-2 text-sm text-white hover:bg-blue-700 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500"
            onClick$={() => onCreateClick$()}
          >
            Создать встречу
          </button>
        </div>

        <div class="mb-4 flex flex-wrap gap-4">
          <div class="flex items-center gap-2">
            <label class="text-sm text-muted" for="meeting-status-filter">Статус:</label>
            <select
              id="meeting-status-filter"
              class="rounded border border-border bg-bg px-2 py-1 text-sm text-text focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500"
              value={statusFilter.value}
              onChange$={(_, el) => {
                statusFilter.value = el.value as "all" | "scheduled" | "canceled" | "ended";
              }}
            >
              <option value="all">Все</option>
              <option value="scheduled">scheduled</option>
              <option value="canceled">canceled</option>
              <option value="ended">ended</option>
            </select>
          </div>

          <div class="flex items-center gap-2">
            <label class="text-sm text-muted" for="meeting-sort-by">Сортировка:</label>
            <select
              id="meeting-sort-by"
              class="rounded border border-border bg-bg px-2 py-1 text-sm text-text focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500"
              value={sortBy.value}
              onChange$={(_, el) => {
                sortBy.value = el.value as "startsAt" | "title";
              }}
            >
              <option value="startsAt">По дате начала</option>
              <option value="title">По названию</option>
            </select>
          </div>
        </div>

        {!hasFilteredMeetings ? (
          <div class="flex flex-col items-center justify-center rounded border border-dashed border-border py-16 text-center">
            <div class="mb-4 h-12 w-12 rounded-full bg-bg" aria-hidden="true" />
            <h2 class="mb-2 text-lg font-semibold text-text">
              {hasSourceMeetings ? "Нет встреч по выбранному фильтру" : "Нет встреч в этой комнате"}
            </h2>
            <p class="mb-4 text-sm text-muted">
              {hasSourceMeetings ? "Сбросьте фильтр, чтобы увидеть все встречи" : "Создайте первую встречу"}
            </p>
            <button
              type="button"
              class="rounded bg-blue-600 px-4 py-2 text-sm text-white hover:bg-blue-700 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500"
              onClick$={() => {
                if (hasSourceMeetings) {
                  statusFilter.value = "all";
                  return;
                }
                void onCreateClick$();
              }}
            >
              {hasSourceMeetings ? "Сбросить фильтр" : "Создать встречу"}
            </button>
          </div>
        ) : (
          <>
            <div class="grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-3">
              {filteredMeetings.value.map((meeting) => (
                <MeetingCard
                  key={meeting.meetingId}
                  meeting={meeting}
                  onEdit$={onEdit$}
                  onCancel$={onCancel$}
                  onParticipants$={onParticipants$}
                  onInvites$={onInvites$}
                />
              ))}
            </div>

            <p class="mt-4 text-sm text-muted">
              Показано {filteredMeetings.value.length} из {totalElements} встреч
            </p>
          </>
        )}
      </section>
    );
  },
);
