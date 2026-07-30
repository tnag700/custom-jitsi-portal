import { component$ } from "@qwik.dev/core";
import { Form } from "@qwik.dev/router";

interface ParticipantSelfAssignmentProps {
  meetingId: string;
  currentUserId: string;
  currentUserDisplayName: string;
  isAssigned: boolean;
  bulkAssignAction: unknown;
  isAssigning: boolean;
}

export const ParticipantSelfAssignment =
  component$<ParticipantSelfAssignmentProps>(
    ({
      meetingId,
      currentUserId,
      currentUserDisplayName,
      isAssigned,
      bulkAssignAction,
      isAssigning,
    }) => (
      <section class="rounded-xl border border-blue-200 bg-blue-50/70 p-4 dark:border-blue-800 dark:bg-blue-950/40 sm:p-5">
        <div class="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
          <div class="flex min-w-0 items-start gap-3">
            <span
              class="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-blue-600 text-sm font-semibold text-white"
              aria-hidden="true"
            >
              Вы
            </span>
            <div class="min-w-0">
              <p class="text-xs font-medium uppercase tracking-wide text-blue-700 dark:text-blue-200">
                Текущая учётная запись
              </p>
              <p class="truncate text-sm font-semibold text-text">
                {currentUserDisplayName}
              </p>
              <p class="mt-1 text-xs text-muted">
                {isAssigned
                  ? "Вы уже добавлены в состав этой встречи."
                  : "Добавьте себя как участника. Роль можно изменить после добавления."}
              </p>
            </div>
          </div>

          {isAssigned ? (
            <span class="w-fit shrink-0 rounded-full bg-emerald-100 px-3 py-1.5 text-xs font-medium text-emerald-800 dark:bg-emerald-900 dark:text-emerald-100">
              Вы уже в составе
            </span>
          ) : (
            <Form action={bulkAssignAction as never} class="shrink-0">
              <input type="hidden" name="meetingId" value={meetingId} />
              <input
                type="hidden"
                name="defaultRole"
                value="participant"
              />
              <input
                type="hidden"
                name="subjectIds[]"
                value={currentUserId}
              />
              <button
                type="submit"
                class="w-full rounded-lg bg-blue-600 px-4 py-2.5 text-sm font-medium text-white hover:bg-blue-700 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500 disabled:cursor-not-allowed disabled:opacity-50 sm:w-auto"
                disabled={isAssigning}
              >
                {isAssigning ? "Добавление..." : "Добавить себя"}
              </button>
            </Form>
          )}
        </div>
      </section>
    ),
  );
