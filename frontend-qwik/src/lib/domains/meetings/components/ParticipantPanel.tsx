import { $, component$, useSignal, type QRL } from "@qwik.dev/core";
import { Form } from "@qwik.dev/router";
import { ApiErrorAlert, formatDateTime } from "~/lib/shared";
import type { Meeting, MeetingErrorPayload, ParticipantAssignment } from "../types";

interface ParticipantPanelProps {
  meeting: Meeting;
  participants: ParticipantAssignment[];
  assignAction: unknown;
  updateRoleAction: unknown;
  unassignAction: unknown;
  onClose$: QRL<() => void>;
  error?: MeetingErrorPayload;
}

const ERROR_MESSAGES: Record<string, string> = {
  ASSIGNMENT_NOT_FOUND: "Назначение не найдено",
  MEETING_ROLE_CONFLICT: "Конфликт ролей",
  INVALID_ROLE: "Недопустимая роль",
};

export const ParticipantPanel = component$<ParticipantPanelProps>(
  ({ meeting, participants, assignAction, updateRoleAction, unassignAction, onClose$, error }) => {
    const subjectId = useSignal("");
    const role = useSignal<"host" | "moderator" | "participant">("participant");

    const errorMessage = error ? ERROR_MESSAGES[error.errorCode] ?? error.detail : null;

    const handleDeleteConfirm$ = $((event: Event) => {
      if (typeof window !== "undefined") {
        const ok = window.confirm("Удалить участника из встречи?");
        if (!ok) {
          event.preventDefault();
        }
      }
    });

    return (
      <aside class="fixed inset-0 z-50 flex justify-end bg-black/40">
        <div class="h-full w-full max-w-xl overflow-y-auto border-l border-border bg-surface p-6">
          <div class="mb-4 flex items-start justify-between gap-3">
            <div>
              <h2 class="text-xl font-semibold text-text">Участники</h2>
              <p class="text-sm text-muted">{meeting.title}</p>
            </div>
            <button
              type="button"
              class="rounded border border-border px-3 py-1 text-sm text-text hover:bg-bg focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500"
              onClick$={() => onClose$()}
            >
              Закрыть
            </button>
          </div>

          {errorMessage && (
            <div class="mb-4" role="alert">
              <ApiErrorAlert
                title="Ошибка управления участниками"
                message={errorMessage}
                errorCode={error?.errorCode}
                traceId={error?.traceId}
              />
            </div>
          )}

          <div class="mb-6 rounded border border-border bg-bg p-4">
            <h3 class="mb-3 text-sm font-semibold text-text">Добавить участника</h3>
            <Form action={assignAction as never} class="grid grid-cols-1 gap-3 md:grid-cols-3">
              <input type="hidden" name="meetingId" value={meeting.meetingId} />
              <div class="md:col-span-2">
                <label class="mb-1 block text-xs text-muted" for="participant-subject-id">subjectId *</label>
                <input
                  id="participant-subject-id"
                  type="text"
                  name="subjectId"
                  value={subjectId.value}
                  class="w-full rounded border border-border bg-surface px-3 py-2 text-sm text-text focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500"
                  onInput$={(_, el) => {
                    subjectId.value = el.value;
                  }}
                />
              </div>
              <div>
                <label class="mb-1 block text-xs text-muted" for="participant-role">role *</label>
                <select
                  id="participant-role"
                  name="role"
                  value={role.value}
                  class="w-full rounded border border-border bg-surface px-3 py-2 text-sm text-text focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500"
                  onChange$={(_, el) => {
                    role.value = el.value as "host" | "moderator" | "participant";
                  }}
                >
                  <option value="host">host</option>
                  <option value="moderator">moderator</option>
                  <option value="participant">participant</option>
                </select>
              </div>
              <div class="md:col-span-3 flex justify-end">
                <button
                  type="submit"
                  class="rounded bg-blue-600 px-4 py-2 text-sm text-white hover:bg-blue-700 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500"
                >
                  Добавить
                </button>
              </div>
            </Form>
          </div>

          <div class="space-y-3">
            {participants.length === 0 ? (
              <p class="rounded border border-dashed border-border p-4 text-sm text-muted">Нет назначенных участников</p>
            ) : (
              participants.map((participant) => {
                const roleClass =
                  participant.role === "host"
                    ? "bg-red-100 text-red-700 dark:bg-red-900 dark:text-red-200"
                    : participant.role === "moderator"
                      ? "bg-yellow-100 text-yellow-800 dark:bg-yellow-900 dark:text-yellow-200"
                      : "bg-blue-100 text-blue-700 dark:bg-blue-900 dark:text-blue-200";

                return (
                  <div key={participant.assignmentId} class="rounded border border-border bg-bg p-4">
                    <div class="mb-2 flex items-center justify-between gap-3">
                      <div>
                        <p class="text-sm font-semibold text-text">{participant.subjectId}</p>
                        {participant.fullName && <p class="text-xs text-muted">{participant.fullName}</p>}
                      </div>
                      <span class={["rounded px-2 py-0.5 text-xs font-medium", roleClass]}>{participant.role}</span>
                    </div>

                    <p class="mb-3 text-xs text-muted">Назначен: {formatDateTime(participant.assignedAt)}</p>

                    <div class="flex flex-wrap items-center gap-2">
                      <Form action={updateRoleAction as never} class="flex items-center gap-2">
                        <input type="hidden" name="meetingId" value={meeting.meetingId} />
                        <input type="hidden" name="subjectId" value={participant.subjectId} />
                        <select
                          name="role"
                          value={participant.role}
                          class="rounded border border-border bg-surface px-2 py-1 text-sm text-text focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500"
                        >
                          <option value="host">host</option>
                          <option value="moderator">moderator</option>
                          <option value="participant">participant</option>
                        </select>
                        <button
                          type="submit"
                          class="rounded border border-border px-3 py-1 text-sm text-text hover:bg-surface focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500"
                        >
                          Обновить роль
                        </button>
                      </Form>

                      <Form action={unassignAction as never} onSubmit$={handleDeleteConfirm$}>
                        <input type="hidden" name="meetingId" value={meeting.meetingId} />
                        <input type="hidden" name="subjectId" value={participant.subjectId} />
                        <button
                          type="submit"
                          class="rounded border border-red-300 px-3 py-1 text-sm text-red-700 hover:bg-red-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-red-500 dark:border-red-700 dark:text-red-300 dark:hover:bg-red-950"
                        >
                          Удалить
                        </button>
                      </Form>
                    </div>
                  </div>
                );
              })
            )}
          </div>
        </div>
      </aside>
    );
  },
);
