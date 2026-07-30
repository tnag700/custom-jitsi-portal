import { component$, type QRL } from "@qwik.dev/core";
import { Form } from "@qwik.dev/router";
import { formatDateTime } from "~/lib/shared";
import type { ParticipantAssignment } from "../types";

interface ParticipantCurrentListProps {
  meetingId: string;
  currentUserId: string;
  participants: ParticipantAssignment[];
  updateRoleAction: unknown;
  unassignAction: unknown;
  onDeleteConfirm$: QRL<(event: Event) => void>;
}

const ROLE_LABELS: Record<ParticipantAssignment["role"], string> = {
  host: "Организатор",
  moderator: "Модератор",
  participant: "Участник",
};

function getRoleClass(role: ParticipantAssignment["role"]): string {
  if (role === "host") {
    return "bg-red-100 text-red-700 dark:bg-red-900 dark:text-red-200";
  }
  if (role === "moderator") {
    return "bg-yellow-100 text-yellow-800 dark:bg-yellow-900 dark:text-yellow-200";
  }
  return "bg-blue-100 text-blue-700 dark:bg-blue-900 dark:text-blue-200";
}

export const ParticipantCurrentList =
  component$<ParticipantCurrentListProps>(
    ({
      meetingId,
      currentUserId,
      participants,
      updateRoleAction,
      unassignAction,
      onDeleteConfirm$,
    }) => (
      <section class="rounded-xl border border-border bg-bg/60 p-4 sm:p-5">
        <div class="mb-4 flex flex-wrap items-start justify-between gap-3">
          <div>
            <h3 class="text-base font-semibold text-text">
              Текущие участники
            </h3>
            <p class="mt-1 text-xs leading-5 text-muted">
              Роли и состав встречи изменяются без перехода на другой экран.
            </p>
          </div>
          <span class="rounded-full border border-border bg-surface px-2.5 py-1 text-xs text-muted">
            {participants.length}
          </span>
        </div>

        {participants.length === 0 ? (
          <p class="rounded-lg border border-dashed border-border bg-surface p-5 text-center text-sm text-muted">
            Пока никто не назначен
          </p>
        ) : (
          <div class="space-y-3">
            {participants.map((participant) => {
              const isCurrentUser =
                participant.subjectId === currentUserId;

              return (
                <article
                  key={participant.assignmentId}
                  class="rounded-lg border border-border bg-surface p-4"
                >
                  <div class="mb-3 flex items-start justify-between gap-3">
                    <div class="min-w-0">
                      <div class="flex flex-wrap items-center gap-2">
                        <p class="truncate text-sm font-semibold text-text">
                          {participant.fullName || participant.subjectId}
                        </p>
                        {isCurrentUser && (
                          <span class="rounded-full bg-blue-100 px-2 py-0.5 text-xs font-medium text-blue-700 dark:bg-blue-900 dark:text-blue-100">
                            Вы
                          </span>
                        )}
                      </div>
                      {participant.fullName && (
                        <p class="truncate text-xs text-muted">
                          {participant.subjectId}
                        </p>
                      )}
                    </div>
                    <span
                      class={[
                        "shrink-0 rounded-full px-2.5 py-1 text-xs font-medium",
                        getRoleClass(participant.role),
                      ]}
                    >
                      {ROLE_LABELS[participant.role]}
                    </span>
                  </div>

                  <p class="mb-3 text-xs text-muted">
                    Назначен: {formatDateTime(participant.assignedAt)}
                  </p>

                  <div class="flex flex-col gap-2 sm:flex-row sm:items-end sm:justify-between">
                    <Form
                      action={updateRoleAction as never}
                      class="grid min-w-0 flex-1 grid-cols-[minmax(0,1fr)_auto] gap-2"
                    >
                      <input
                        type="hidden"
                        name="meetingId"
                        value={meetingId}
                      />
                      <input
                        type="hidden"
                        name="subjectId"
                        value={participant.subjectId}
                      />
                      <label
                        class="sr-only"
                        for={`role-${participant.assignmentId}`}
                      >
                        Роль участника{" "}
                        {participant.fullName || participant.subjectId}
                      </label>
                      <select
                        id={`role-${participant.assignmentId}`}
                        name="role"
                        value={participant.role}
                        class="min-w-0 rounded-lg border border-border bg-surface px-3 py-2 text-sm text-text focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500"
                      >
                        <option
                          value="host"
                          selected={participant.role === "host"}
                        >
                          Организатор
                        </option>
                        <option
                          value="moderator"
                          selected={participant.role === "moderator"}
                        >
                          Модератор
                        </option>
                        <option
                          value="participant"
                          selected={participant.role === "participant"}
                        >
                          Участник
                        </option>
                      </select>
                      <button
                        type="submit"
                        class="rounded-lg border border-border px-3 py-2 text-sm font-medium text-text hover:bg-bg focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500"
                      >
                        Сохранить
                      </button>
                    </Form>

                    <Form
                      action={unassignAction as never}
                      onSubmit$={onDeleteConfirm$}
                    >
                      <input
                        type="hidden"
                        name="meetingId"
                        value={meetingId}
                      />
                      <input
                        type="hidden"
                        name="subjectId"
                        value={participant.subjectId}
                      />
                      <button
                        type="submit"
                        class="w-full rounded-lg border border-red-300 px-3 py-2 text-sm text-red-700 hover:bg-red-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-red-500 dark:border-red-700 dark:text-red-300 dark:hover:bg-red-950 sm:w-auto"
                      >
                        Удалить
                      </button>
                    </Form>
                  </div>
                </article>
              );
            })}
          </div>
        )}
      </section>
    ),
  );
