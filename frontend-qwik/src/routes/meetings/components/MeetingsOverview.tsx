import { component$, type QRL, type Signal } from "@qwik.dev/core";
import { Link } from "@qwik.dev/router";
import type {
  Meeting,
  MeetingErrorPayload,
  MeetingFormAction,
} from "~/lib/domains/meetings";
import { MeetingForm, MeetingList } from "~/lib/domains/meetings";
import type { Room } from "~/lib/domains/rooms";
import {
  type ActionValidationFeedback,
  buildMeetingsHref,
} from "../meetings-page-state";

interface MeetingsOverviewProps {
  rooms: Room[];
  meetings: Meeting[];
  totalMeetings: number;
  selectedRoomId: string;
  editingMeeting: Signal<Meeting | null>;
  showCreateForm: Signal<boolean>;
  showEditForm: Signal<boolean>;
  createAction: unknown;
  updateAction: unknown;
  createRunning: boolean;
  updateRunning: boolean;
  createError?: MeetingErrorPayload;
  updateError?: MeetingErrorPayload;
  createValidationFeedback?: ActionValidationFeedback;
  updateValidationFeedback?: ActionValidationFeedback;
  onRoomChange$: QRL<(roomId: string) => void>;
  onEdit$: QRL<(meeting: Meeting) => void>;
  onCancel$: QRL<(meeting: Meeting) => void>;
  onParticipants$: QRL<(meeting: Meeting) => void>;
  onInvites$: QRL<(meeting: Meeting) => void>;
  onCreate$: QRL<() => void>;
}

export const MeetingsOverview = component$<MeetingsOverviewProps>(
  ({
    rooms,
    meetings,
    totalMeetings,
    selectedRoomId,
    editingMeeting,
    showCreateForm,
    showEditForm,
    createAction,
    updateAction,
    createRunning,
    updateRunning,
    createError,
    updateError,
    createValidationFeedback,
    updateValidationFeedback,
    onRoomChange$,
    onEdit$,
    onCancel$,
    onParticipants$,
    onInvites$,
    onCreate$,
  }) => {
    const selectedRoom =
      rooms.find((room) => room.roomId === selectedRoomId) ?? null;

    return (
      <>
        <header class="flex flex-col gap-2 sm:flex-row sm:items-end sm:justify-between">
          <div>
            <p class="text-xs font-medium uppercase tracking-wide text-blue-600 dark:text-blue-300">
              Рабочая область
            </p>
            <h1 class="mt-1 text-2xl font-bold text-text">Встречи</h1>
            <p class="mt-1 max-w-2xl text-sm text-muted">
              Планируйте видеовстречи, управляйте участниками и гостевыми
              ссылками в контексте выбранной комнаты.
            </p>
          </div>
          {selectedRoom && (
            <span class="w-fit rounded-full border border-blue-200 bg-blue-50 px-3 py-1 text-xs font-medium text-blue-700 dark:border-blue-800 dark:bg-blue-950 dark:text-blue-200">
              {selectedRoom.name}
            </span>
          )}
        </header>

        {rooms.length === 0 ? (
          <section class="rounded-xl border border-dashed border-border bg-surface px-6 py-12 text-center">
            <div
              class="mx-auto mb-4 flex h-12 w-12 items-center justify-center rounded-full bg-blue-50 text-xl text-blue-700 dark:bg-blue-950 dark:text-blue-200"
              aria-hidden="true"
            >
              +
            </div>
            <h2 class="text-lg font-semibold text-text">
              Пока нет активных комнат
            </h2>
            <p class="mx-auto mt-2 max-w-md text-sm text-muted">
              Создайте или активируйте комнату, после чего здесь появится
              расписание встреч.
            </p>
            <Link
              href="/rooms"
              class="mt-5 inline-flex rounded-lg bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500"
            >
              Перейти к комнатам
            </Link>
          </section>
        ) : (
          <>
            <section class="rounded-xl border border-border bg-surface p-4 sm:p-5">
              {rooms.length === 1 ? (
                <div class="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
                  <div>
                    <p class="text-xs font-medium uppercase tracking-wide text-muted">
                      Активная комната
                    </p>
                    <p class="mt-1 font-semibold text-text">{rooms[0].name}</p>
                    <p class="mt-1 text-sm text-muted">
                      {selectedRoomId
                        ? "Расписание этой комнаты открыто."
                        : "Откройте расписание, чтобы создать встречу или управлять участниками."}
                    </p>
                  </div>
                  {!selectedRoomId && (
                    <Link
                      href={buildMeetingsHref(rooms[0].roomId)}
                      class="inline-flex w-fit rounded-lg bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500"
                    >
                      Открыть расписание
                    </Link>
                  )}
                </div>
              ) : (
                <div class="min-w-0">
                  <label
                    class="mb-1 block text-sm font-medium text-text"
                    for="room-selector"
                  >
                    Комната
                  </label>
                  <select
                    id="room-selector"
                    class="w-full rounded-lg border border-border bg-bg px-3 py-2.5 text-text focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500"
                    value={selectedRoomId}
                    onChange$={(_, element) => onRoomChange$(element.value)}
                  >
                    <option value="" selected={!selectedRoomId}>
                      Выберите комнату
                    </option>
                    {rooms.map((room) => (
                      <option
                        key={room.roomId}
                        value={room.roomId}
                        selected={selectedRoomId === room.roomId}
                      >
                        {room.name}
                      </option>
                    ))}
                  </select>
                </div>
              )}
            </section>

            {selectedRoomId ? (
              <MeetingList
                meetings={meetings}
                totalElements={totalMeetings}
                onEdit$={onEdit$}
                onCancel$={onCancel$}
                onParticipants$={onParticipants$}
                onInvites$={onInvites$}
                onCreateClick$={onCreate$}
              />
            ) : (
              <section class="rounded-xl border border-dashed border-border bg-surface px-6 py-12 text-center">
                <h2 class="text-lg font-semibold text-text">
                  Выберите комнату
                </h2>
                <p class="mt-2 text-sm text-muted">
                  Расписание и инструменты управления загрузятся для выбранной
                  комнаты.
                </p>
              </section>
            )}
          </>
        )}

        {selectedRoomId && showCreateForm.value && (
          <MeetingForm
            action={createAction as unknown as MeetingFormAction}
            roomId={selectedRoomId}
            isLoading={createRunning}
            error={createError}
            validationFeedback={createValidationFeedback}
            isOpen={showCreateForm}
          />
        )}

        {showEditForm.value && editingMeeting.value && (
          <MeetingForm
            action={updateAction as unknown as MeetingFormAction}
            roomId={editingMeeting.value.roomId}
            meeting={editingMeeting.value}
            isLoading={updateRunning}
            error={updateError}
            validationFeedback={updateValidationFeedback}
            isOpen={showEditForm}
          />
        )}
      </>
    );
  },
);
