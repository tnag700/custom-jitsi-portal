import { $, component$, useSignal, useTask$, type QRL } from "@qwik.dev/core";
import { Form, useLocation, useNavigate } from "@qwik.dev/router";
import { ApiErrorAlert, formatDateTime } from "~/lib/shared";
import type { Meeting, MeetingErrorPayload, ParticipantAssignment, UserProfileSummary } from "../types";

type BulkAssignActionValue = {
  success?: boolean;
  assignments?: ParticipantAssignment[];
  error?: MeetingErrorPayload;
  failed?: boolean;
  formErrors?: string[];
  fieldErrors?: Record<string, unknown>;
  [key: string]: unknown;
};

interface ParticipantPanelProps {
  meeting: Meeting;
  participants: ParticipantAssignment[];
  assignableUsers: UserProfileSummary[];
  assignAction: unknown;
  bulkAssignAction: unknown;
  updateRoleAction: unknown;
  unassignAction: unknown;
  onClose$: QRL<() => void>;
  error?: MeetingErrorPayload;
}

interface BulkAssignActionLike {
  isRunning?: boolean;
  value?: BulkAssignActionValue;
}

type SortMode = "fullName" | "organization";

const ERROR_MESSAGES: Record<string, string> = {
  ASSIGNMENT_NOT_FOUND: "Назначение не найдено",
  MEETING_ROLE_CONFLICT: "Конфликт ролей",
  INVALID_ROLE: "Недопустимая роль",
};

export const ParticipantPanel = component$<ParticipantPanelProps>(
  ({ meeting, participants, assignableUsers, bulkAssignAction, updateRoleAction, unassignAction, onClose$, error }) => {
    const loc = useLocation();
    const nav = useNavigate();
    const selectedIds = useSignal<string[]>([]);
    const bulkRole = useSignal<"host" | "moderator" | "participant">("participant");
    const searchQuery = useSignal(loc.url.searchParams.get("participantQuery") ?? "");
    const organizationFilter = useSignal(loc.url.searchParams.get("participantOrganization") ?? "");
    const sortMode = useSignal<SortMode>((loc.url.searchParams.get("participantSort") as SortMode) || "fullName");

    const errorMessage = error ? ERROR_MESSAGES[error.errorCode] ?? error.detail : null;
    const assignedSubjectIds = new Set(participants.map((participant) => participant.subjectId));
    const organizations = [...new Set(assignableUsers.map((user) => user.organization).filter(Boolean))].sort((left, right) =>
      left.localeCompare(right, "ru"),
    );
    const sortedUsers = [...assignableUsers].sort((left, right) => {
      if (sortMode.value === "organization") {
        const byOrg = left.organization.localeCompare(right.organization, "ru");
        if (byOrg !== 0) {
          return byOrg;
        }
      }
      return left.fullName.localeCompare(right.fullName, "ru");
    });
    const selectableUsers = sortedUsers.filter((user) => !assignedSubjectIds.has(user.subjectId));
    const selectedCount = selectedIds.value.length;
    const allVisibleSelected = selectableUsers.length > 0 && selectableUsers.every((user) => selectedIds.value.includes(user.subjectId));
    const bulkActionState = bulkAssignAction as BulkAssignActionLike;
    const panelRef = useSignal<HTMLDivElement>();

    useTask$(({ track }) => {
      const isSuccess = track(() => bulkActionState.value?.success === true);
      if (isSuccess) {
        selectedIds.value = [];
      }
    });

    const handleDeleteConfirm$ = $((event: Event) => {
      if (typeof window !== "undefined") {
        const ok = window.confirm("Удалить участника из встречи?");
        if (!ok) {
          event.preventDefault();
        }
      }
    });

    const updateParticipantQuery$ = $(() => {
      const params = new URLSearchParams(loc.url.searchParams);

      const trimmedQuery = searchQuery.value.trim();
      if (trimmedQuery.length > 0) {
        params.set("participantQuery", trimmedQuery);
      } else {
        params.delete("participantQuery");
      }

      const trimmedOrganization = organizationFilter.value.trim();
      if (trimmedOrganization.length > 0) {
        params.set("participantOrganization", trimmedOrganization);
      } else {
        params.delete("participantOrganization");
      }

      params.set("participantSort", sortMode.value);
      void nav(`${loc.url.pathname}?${params.toString()}`);
    });

    const resetParticipantFilters$ = $(() => {
      searchQuery.value = "";
      organizationFilter.value = "";
      sortMode.value = "fullName";
      const params = new URLSearchParams(loc.url.searchParams);
      params.delete("participantQuery");
      params.delete("participantOrganization");
      params.delete("participantSort");
      void nav(`${loc.url.pathname}?${params.toString()}`);
    });

    const toggleSelectedUser$ = $((subjectId: string, checked: boolean) => {
      if (checked) {
        if (!selectedIds.value.includes(subjectId)) {
          selectedIds.value = [...selectedIds.value, subjectId];
        }
        return;
      }

      selectedIds.value = selectedIds.value.filter((id) => id !== subjectId);
    });

    const toggleSelectAllVisible$ = $((checked: boolean) => {
      if (checked) {
        selectedIds.value = selectableUsers.map((user) => user.subjectId);
        return;
      }

      selectedIds.value = [];
    });

    const requestClose$ = $(() => {
      if (typeof window === "undefined") {
        void onClose$();
        return;
      }

      window.requestAnimationFrame(() => {
        void onClose$();
      });
    });

    const handleBackdropClick$ = $((event: MouseEvent, overlay: HTMLElement) => {
      if (event.target === overlay) {
        void requestClose$();
      }
    });

    const handleKeyDown$ = $((event: KeyboardEvent) => {
      if (event.key !== "Escape") {
        return;
      }

      event.preventDefault();
      void requestClose$();
    });

    useTask$(({ track }) => {
      track(() => panelRef.value);
      if (typeof window !== "undefined" && panelRef.value) {
        queueMicrotask(() => {
          panelRef.value?.focus();
        });
      }
    });

    return (
      <aside
        class="fixed inset-0 z-50 flex justify-end bg-black/40"
        role="presentation"
        onClick$={handleBackdropClick$}
        onKeyDown$={handleKeyDown$}
      >
        <div
          ref={panelRef}
          role="dialog"
          aria-modal="true"
          aria-labelledby="participant-panel-title"
          tabIndex={-1}
          class="h-full w-full max-w-xl overflow-y-auto border-l border-border bg-surface p-6"
        >
          <div class="mb-4 flex items-start justify-between gap-3">
            <div>
              <h2 id="participant-panel-title" class="text-xl font-semibold text-text">Участники</h2>
              <p class="text-sm text-muted">{meeting.title}</p>
            </div>
            <button
              type="button"
              class="rounded border border-border px-3 py-1 text-sm text-text hover:bg-bg focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500"
              onClick$={requestClose$}
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

          <section class="mb-6 rounded border border-border bg-bg p-4">
            <div class="mb-3 flex items-center justify-between gap-3">
              <div>
                <h3 class="text-sm font-semibold text-text">Текущие участники</h3>
                <p class="text-xs text-muted">Удаление и изменение роли выполняются в этом же окне, без перехода в другой экран.</p>
              </div>
              <span class="rounded bg-surface px-2 py-1 text-xs text-muted">Всего: {participants.length}</span>
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
                    <div key={participant.assignmentId} class="rounded border border-border bg-surface p-4">
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
          </section>

          <section class="mb-6 rounded border border-border bg-bg p-4">
            <div class="mb-3 flex items-center justify-between gap-3">
              <div>
                <h3 class="text-sm font-semibold text-text">Добавить участников из каталога</h3>
                <p class="text-xs text-muted">Выберите пользователей по ФИО или учреждению, затем добавьте их одной операцией.</p>
              </div>
              {assignableUsers.length === 50 && (
                <span class="rounded bg-amber-50 px-2 py-1 text-xs text-amber-700">Показаны первые 50 результатов</span>
              )}
            </div>

            <div class="grid grid-cols-1 gap-3 md:grid-cols-3">
              <div class="md:col-span-2">
                <label class="mb-1 block text-xs text-muted" for="participant-search">Поиск по ФИО</label>
                <input
                  id="participant-search"
                  type="text"
                  value={searchQuery.value}
                  placeholder="Например, Иванов Иван"
                  class="w-full rounded border border-border bg-surface px-3 py-2 text-sm text-text focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500"
                  onInput$={(_, el) => {
                    searchQuery.value = el.value;
                  }}
                />
              </div>
              <div>
                <label class="mb-1 block text-xs text-muted" for="participant-organization-filter">Учреждение</label>
                <select
                  id="participant-organization-filter"
                  value={organizationFilter.value}
                  class="w-full rounded border border-border bg-surface px-3 py-2 text-sm text-text focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500"
                  onChange$={(_, el) => {
                    organizationFilter.value = el.value;
                  }}
                >
                  <option value="">Все учреждения</option>
                  {organizations.map((organization) => (
                    <option key={organization} value={organization}>{organization}</option>
                  ))}
                </select>
              </div>
              <div>
                <label class="mb-1 block text-xs text-muted" for="participant-sort">Сортировка</label>
                <select
                  id="participant-sort"
                  value={sortMode.value}
                  class="w-full rounded border border-border bg-surface px-3 py-2 text-sm text-text focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500"
                  onChange$={(_, el) => {
                    sortMode.value = el.value as SortMode;
                  }}
                >
                  <option value="fullName">По ФИО</option>
                  <option value="organization">По учреждению</option>
                </select>
              </div>
              <div class="md:col-span-2 flex flex-wrap items-end gap-2">
                <button
                  type="button"
                  class="rounded bg-blue-600 px-4 py-2 text-sm text-white hover:bg-blue-700 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500"
                  onClick$={updateParticipantQuery$}
                >
                  Найти
                </button>
                <button
                  type="button"
                  class="rounded border border-border px-4 py-2 text-sm text-text hover:bg-surface focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500"
                  onClick$={resetParticipantFilters$}
                >
                  Сбросить
                </button>
              </div>
            </div>

            <div class="mt-4 rounded border border-border bg-surface">
              <div class="flex flex-wrap items-center justify-between gap-3 border-b border-border px-4 py-3">
                <label class="flex items-center gap-2 text-sm text-text">
                  <input
                    type="checkbox"
                    checked={allVisibleSelected}
                    disabled={selectableUsers.length === 0}
                    onChange$={(_, el) => toggleSelectAllVisible$(el.checked)}
                  />
                  <span>Выбрать всех найденных</span>
                </label>
                <p class="text-xs text-muted">Найдено: {assignableUsers.length} | Доступно: {selectableUsers.length} | Выбрано: {selectedCount}</p>
              </div>

              <div class="max-h-80 overflow-y-auto">
                {sortedUsers.length === 0 ? (
                  <p class="p-4 text-sm text-muted">По текущему фильтру пользователи не найдены.</p>
                ) : (
                  sortedUsers.map((user) => {
                    const assigned = assignedSubjectIds.has(user.subjectId);
                    const checked = selectedIds.value.includes(user.subjectId);

                    return (
                      <label
                        key={user.subjectId}
                        class={[
                          "flex cursor-pointer items-start gap-3 border-b border-border px-4 py-3 last:border-b-0",
                          assigned ? "bg-bg/60 opacity-70" : "hover:bg-bg/60",
                        ]}
                      >
                        <input
                          type="checkbox"
                          checked={checked}
                          disabled={assigned}
                          onChange$={(_, el) => toggleSelectedUser$(user.subjectId, el.checked)}
                        />
                        <div class="min-w-0 flex-1">
                          <div class="flex flex-wrap items-center gap-2">
                            <p class="text-sm font-semibold text-text">{user.fullName}</p>
                            {assigned && (
                              <span class="rounded bg-gray-100 px-2 py-0.5 text-xs text-gray-700">Уже участник</span>
                            )}
                          </div>
                          <p class="text-xs text-muted">{user.organization}</p>
                          <p class="text-xs text-muted">{user.position}</p>
                        </div>
                      </label>
                    );
                  })
                )}
              </div>
            </div>

            <Form action={bulkAssignAction as never} class="mt-4 flex flex-wrap items-end justify-between gap-3 rounded border border-border bg-surface p-4">
              <input type="hidden" name="meetingId" value={meeting.meetingId} />
              <input type="hidden" name="defaultRole" value={bulkRole.value} />
              {selectedIds.value.map((subjectId) => (
                <input key={subjectId} type="hidden" name="subjectIds[]" value={subjectId} />
              ))}

              <div>
                <label class="mb-1 block text-xs text-muted" for="participant-bulk-role">Роль для выбранных</label>
                <select
                  id="participant-bulk-role"
                  value={bulkRole.value}
                  class="rounded border border-border bg-surface px-3 py-2 text-sm text-text focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500"
                  onChange$={(_, el) => {
                    bulkRole.value = el.value as "host" | "moderator" | "participant";
                  }}
                >
                  <option value="host">host</option>
                  <option value="moderator">moderator</option>
                  <option value="participant">participant</option>
                </select>
              </div>

              <div class="flex flex-wrap gap-2">
                <button
                  type="button"
                  class="rounded border border-border px-4 py-2 text-sm text-text hover:bg-bg focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500"
                  onClick$={() => {
                    selectedIds.value = [];
                  }}
                  disabled={selectedCount === 0}
                >
                  Снять выбор
                </button>
                <button
                  type="submit"
                  class="rounded bg-blue-600 px-4 py-2 text-sm text-white hover:bg-blue-700 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500 disabled:opacity-50"
                  disabled={selectedCount === 0 || !!bulkActionState.isRunning}
                >
                  {bulkActionState.isRunning ? "Добавление..." : `Добавить выбранных (${selectedCount})`}
                </button>
              </div>
            </Form>
          </section>
        </div>
      </aside>
    );
  },
);
