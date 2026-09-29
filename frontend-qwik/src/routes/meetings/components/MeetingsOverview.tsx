import { component$, type QRL, type Signal } from "@qwik.dev/core";
import { Link } from "@qwik.dev/router";
import type {
  Meeting,
  MeetingErrorPayload,
  MeetingFormAction,
} from "~/lib/domains/meetings";
import { MeetingForm, MeetingList } from "~/lib/domains/meetings";
import type { Room } from "~/lib/domains/rooms";
import { formatDateTime } from "~/lib/shared";
import { PageNavigation } from "~/lib/shared/components/PageNavigation";
import type { RoomManagementRequest } from "../../rooms/components/RoomManagement";
import {
  type ActionValidationFeedback,
  buildMeetingsHref,
} from "../meetings-page-state";

export type RoomMeetingPreview =
  { content: Meeting[]; totalElements: number } | { error: true };

interface MeetingsOverviewProps {
  rooms: Room[];
  totalRooms?: number;
  roomPreviews: Record<string, RoomMeetingPreview>;
  meetings: Meeting[];
  totalMeetings: number;
  roomsPage: number;
  meetingsPage?: number;
  meetingsTotalPages?: number;
  currentUrl?: string;
  selectedRoomId: string;
  createRoomId?: string;
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
  onRoomAction$: QRL<(request: RoomManagementRequest) => void>;
  onEdit$: QRL<(meeting: Meeting) => void>;
  onCancel$: QRL<(meeting: Meeting) => void>;
  onParticipants$: QRL<(meeting: Meeting) => void>;
  onInvites$: QRL<(meeting: Meeting) => void>;
  onCreate$: QRL<(roomId: string) => void>;
}

export const MeetingsOverview = component$<MeetingsOverviewProps>((props) => {
  const {
    rooms,
    totalRooms,
    roomPreviews,
    meetings,
    totalMeetings,
    roomsPage,
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
    onRoomAction$,
    onEdit$,
    onCancel$,
    onParticipants$,
    onInvites$,
    onCreate$,
  } = props;
  const createRoomId = props.createRoomId ?? selectedRoomId;

  return (
    <>
      <header class="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <p class="text-xs font-medium uppercase tracking-wide text-primary">
            Рабочая область
          </p>
          <h1 class="mt-1 text-2xl font-bold text-text">Комнаты и встречи</h1>
          <p class="mt-2 max-w-2xl text-sm text-muted">
            Комната — постоянное место для общения. Внутри неё — встречи со
            своим временем, участниками и приглашениями.
          </p>
        </div>
        <button
          type="button"
          class="shrink-0 rounded-lg bg-primary px-4 py-2.5 text-sm font-medium text-primary-foreground hover:opacity-90 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
          onClick$={() => onRoomAction$({ kind: "create", room: null })}
        >
          + Создать комнату
        </button>
      </header>

      {rooms.length === 0 ? (
        <section class="rounded-2xl border border-dashed border-border bg-surface px-6 py-12 text-center">
          <h2 class="text-lg font-semibold text-text">
            {totalRooms ? "На этой странице нет комнат" : "Начните с комнаты"}
          </h2>
          <p class="mt-2 text-sm text-muted">
            {totalRooms
              ? "Перейдите на другую страницу комнат."
              : "Нажмите «Создать комнату», дайте ей название и добавьте первую встречу."}
          </p>
        </section>
      ) : (
        <div class="grid grid-cols-1 items-start gap-5 xl:grid-cols-2">
          {rooms.map((room) => {
            const selected = room.roomId === selectedRoomId;
            const active = room.status === "active";
            const preview = roomPreviews[room.roomId];
            return (
              <article
                key={room.roomId}
                data-room-id={room.roomId}
                aria-labelledby={`room-${room.roomId}`}
                class={[
                  "min-w-0 rounded-2xl border-2 bg-surface p-4 sm:p-5",
                  selected ? "border-primary xl:col-span-2" : "border-border",
                ]}
              >
                <div class="flex items-start justify-between gap-3">
                  <div class="flex min-w-0 items-start gap-3">
                    <span
                      aria-hidden="true"
                      class="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl bg-primary/10 text-primary"
                    >
                      <svg
                        class="h-6 w-6"
                        viewBox="0 0 24 24"
                        fill="none"
                        stroke="currentColor"
                        stroke-width="1.6"
                      >
                        <path d="M3 21h18M5 21V4a1 1 0 0 1 1-1h12a1 1 0 0 1 1 1v17M9 21v-7h6v7M9 7h1m4 0h1M9 10h1m4 0h1" />
                      </svg>
                    </span>
                    <div class="min-w-0">
                      <p class="text-xs font-medium uppercase tracking-wide text-muted">
                        Комната
                      </p>
                      <h2
                        id={`room-${room.roomId}`}
                        class="mt-1 break-words text-lg font-semibold text-text"
                      >
                        {room.name}
                      </h2>
                    </div>
                  </div>
                  <span
                    class={[
                      "shrink-0 rounded-full px-2 py-1 text-xs font-medium",
                      active
                        ? "bg-green-100 text-green-800 dark:bg-green-950 dark:text-green-200"
                        : "bg-bg text-muted",
                    ]}
                  >
                    {active ? "Активна" : "Закрыта"}
                  </span>
                </div>
                {room.description && (
                  <p class="mt-3 line-clamp-2 break-words text-sm text-muted">
                    {room.description}
                  </p>
                )}
                <div class="my-4 flex flex-wrap items-center justify-between gap-2">
                  <Link
                    href={`${buildMeetingsHref(selected ? "" : room.roomId, {
                      roomsPage,
                    })}#room-${room.roomId}`}
                    aria-expanded={selected}
                    aria-controls={`schedule-${room.roomId}`}
                    class="rounded text-sm font-medium text-primary underline underline-offset-4 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
                  >
                    {selected ? "Свернуть расписание" : "Открыть расписание"}
                  </Link>
                  <details class="relative">
                    <summary
                      class="cursor-pointer rounded-lg border border-border px-3 py-2 text-sm text-muted focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
                      aria-label={`Настройки комнаты ${room.name}`}
                    >
                      Настройки комнаты
                    </summary>
                    <div class="absolute right-0 z-10 mt-2 grid w-52 gap-1 rounded-xl border border-border bg-surface p-2 shadow-lg">
                      <button
                        type="button"
                        class="rounded px-3 py-2 text-left text-sm text-text hover:bg-bg focus-visible:ring-2 focus-visible:ring-primary"
                        onClick$={() => onRoomAction$({ kind: "edit", room })}
                      >
                        Редактировать
                      </button>
                      {active && (
                        <button
                          type="button"
                          class="rounded px-3 py-2 text-left text-sm text-text hover:bg-bg focus-visible:ring-2 focus-visible:ring-primary"
                          onClick$={() =>
                            onRoomAction$({ kind: "close", room })
                          }
                        >
                          Закрыть комнату
                        </button>
                      )}
                      <button
                        type="button"
                        class="rounded px-3 py-2 text-left text-sm text-danger hover:bg-bg focus-visible:ring-2 focus-visible:ring-primary"
                        onClick$={() => onRoomAction$({ kind: "delete", room })}
                      >
                        Удалить комнату
                      </button>
                    </div>
                  </details>
                </div>
                <div
                  id={`schedule-${room.roomId}`}
                  class="rounded-xl bg-bg/70 p-3"
                >
                  {selected ? (
                    <>
                      <MeetingList
                        key={room.roomId}
                        meetings={meetings}
                        totalElements={totalMeetings}
                        canCreate={active}
                        onEdit$={onEdit$}
                        onCancel$={onCancel$}
                        onParticipants$={onParticipants$}
                        onInvites$={onInvites$}
                        onCreateClick$={() => onCreate$(room.roomId)}
                      />
                      <PageNavigation
                        currentUrl={
                          props.currentUrl ?? buildMeetingsHref(room.roomId)
                        }
                        parameter="meetingsPage"
                        page={props.meetingsPage ?? 0}
                        totalPages={props.meetingsTotalPages ?? 1}
                        label={`Страницы встреч комнаты ${room.name}`}
                      />
                    </>
                  ) : (
                    <>
                      <p class="mb-3 text-xs font-medium text-muted">
                        Встречи в комнате
                        {preview && "content" in preview
                          ? ` · ${preview.totalElements}`
                          : ""}
                      </p>
                      {preview && "error" in preview ? (
                        <p role="status" class="mb-3 text-sm text-danger">
                          Не удалось загрузить встречи. Откройте расписание,
                          чтобы повторить попытку.
                        </p>
                      ) : (
                        <div class="grid grid-cols-1 gap-3 sm:grid-cols-2">
                          {preview &&
                            "content" in preview &&
                            preview.content.map((meeting) => (
                              <Link
                                key={meeting.meetingId}
                                href={`${buildMeetingsHref(room.roomId, { roomsPage, meetingId: meeting.meetingId })}#meeting-details`}
                                class="flex min-h-36 min-w-0 flex-col rounded-xl border border-border bg-surface p-3 text-text transition-colors hover:border-primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
                                aria-label={`Открыть встречу ${meeting.title} в комнате ${room.name}`}
                              >
                                <span class="text-xs text-muted">
                                  {meeting.status === "scheduled"
                                    ? "Запланирована"
                                    : meeting.status === "canceled"
                                      ? "Отменена"
                                      : "Завершена"}
                                </span>
                                <span class="mt-2 break-words text-sm font-semibold">
                                  {meeting.title}
                                </span>
                                <span class="mt-auto pt-3 text-xs text-muted">
                                  {formatDateTime(meeting.startsAt)}
                                </span>
                              </Link>
                            ))}
                          {active && (
                            <button
                              type="button"
                              onClick$={() => onCreate$(room.roomId)}
                              class="flex min-h-36 flex-col items-center justify-center gap-2 rounded-xl border border-dashed border-primary/50 p-3 text-sm font-medium text-primary hover:bg-primary/5 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
                              aria-label={`Создать встречу в комнате ${room.name}`}
                            >
                              <span aria-hidden="true" class="text-2xl">
                                +
                              </span>
                              Создать встречу
                            </button>
                          )}
                        </div>
                      )}
                      {preview &&
                        "content" in preview &&
                        preview.totalElements === 0 && (
                          <p class="mt-3 text-xs text-muted">
                            В этой комнате пока нет встреч.
                            {!active && " Комната закрыта для новых встреч."}
                          </p>
                        )}
                      {preview &&
                        "content" in preview &&
                        preview.totalElements > preview.content.length && (
                          <p class="mt-3 text-xs text-muted">
                            Показано {preview.content.length} из{" "}
                            {preview.totalElements}. Все встречи — в расписании.
                          </p>
                        )}
                    </>
                  )}
                </div>
              </article>
            );
          })}
        </div>
      )}

      {createRoomId && showCreateForm.value && (
        <MeetingForm
          action={createAction as MeetingFormAction}
          roomId={createRoomId}
          isLoading={createRunning}
          error={createError}
          validationFeedback={createValidationFeedback}
          isOpen={showCreateForm}
        />
      )}
      {showEditForm.value && editingMeeting.value && (
        <MeetingForm
          action={updateAction as MeetingFormAction}
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
});
