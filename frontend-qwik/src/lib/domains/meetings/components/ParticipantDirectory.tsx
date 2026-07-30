import {
  component$,
  type QRL,
  type Signal,
} from "@qwik.dev/core";
import { Form } from "@qwik.dev/router";
import type { ParticipantAssignment, UserProfileSummary } from "../types";
import {
  toggleSelectedParticipant,
  type ParticipantSortMode,
} from "./participant-panel-state";

interface ParticipantDirectoryProps {
  meetingId: string;
  currentUserId: string;
  users: UserProfileSummary[];
  assignedSubjectIds: string[];
  selectableSubjectIds: string[];
  organizations: string[];
  selectedIds: Signal<string[]>;
  searchQuery: Signal<string>;
  organizationFilter: Signal<string>;
  sortMode: Signal<ParticipantSortMode>;
  bulkRole: Signal<ParticipantAssignment["role"]>;
  bulkAssignAction: unknown;
  isAssigning: boolean;
  onApplyFilters$: QRL<() => void>;
  onResetFilters$: QRL<() => void>;
}

export const ParticipantDirectory = component$<ParticipantDirectoryProps>(
  ({
    meetingId,
    currentUserId,
    users,
    assignedSubjectIds,
    selectableSubjectIds,
    organizations,
    selectedIds,
    searchQuery,
    organizationFilter,
    sortMode,
    bulkRole,
    bulkAssignAction,
    isAssigning,
    onApplyFilters$,
    onResetFilters$,
  }) => {
    const activeSelectedIds = selectedIds.value.filter((subjectId) =>
      selectableSubjectIds.includes(subjectId),
    );
    const selectedCount = activeSelectedIds.length;
    const allVisibleSelected =
      selectableSubjectIds.length > 0 &&
      selectableSubjectIds.every((subjectId) =>
        activeSelectedIds.includes(subjectId),
      );

    return (
      <section class="rounded-xl border border-border bg-bg/60 p-4 sm:p-5">
        <div class="mb-4 flex flex-wrap items-start justify-between gap-3">
          <div>
            <h3 class="text-base font-semibold text-text">
              Каталог пользователей
            </h3>
            <p class="mt-1 max-w-lg text-xs leading-5 text-muted">
              Найдите коллег и добавьте выбранных одной операцией.
            </p>
          </div>
          {users.length === 50 && (
            <span class="rounded-full bg-amber-50 px-2.5 py-1 text-xs text-amber-700 dark:bg-amber-950 dark:text-amber-200">
              Первые 50 результатов
            </span>
          )}
        </div>

        <div class="grid grid-cols-1 gap-3 sm:grid-cols-2">
          <div class="sm:col-span-2">
            <label
              class="mb-1 block text-xs font-medium text-muted"
              for="participant-search"
            >
              Поиск по ФИО
            </label>
            <input
              id="participant-search"
              type="search"
              value={searchQuery.value}
              placeholder="Например, Иванов Иван"
              class="w-full rounded-lg border border-border bg-surface px-3 py-2.5 text-sm text-text focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500"
              onInput$={(_, element) => {
                searchQuery.value = element.value;
              }}
            />
          </div>

          <div>
            <label
              class="mb-1 block text-xs font-medium text-muted"
              for="participant-organization-filter"
            >
              Учреждение
            </label>
            <select
              id="participant-organization-filter"
              value={organizationFilter.value}
              class="w-full rounded-lg border border-border bg-surface px-3 py-2.5 text-sm text-text focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500"
              onChange$={(_, element) => {
                organizationFilter.value = element.value;
              }}
            >
              <option value="" selected={!organizationFilter.value}>
                Все учреждения
              </option>
              {organizations.map((organization) => (
                <option
                  key={organization}
                  value={organization}
                  selected={organizationFilter.value === organization}
                >
                  {organization}
                </option>
              ))}
            </select>
          </div>

          <div>
            <label
              class="mb-1 block text-xs font-medium text-muted"
              for="participant-sort"
            >
              Сортировка
            </label>
            <select
              id="participant-sort"
              value={sortMode.value}
              class="w-full rounded-lg border border-border bg-surface px-3 py-2.5 text-sm text-text focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500"
              onChange$={(_, element) => {
                sortMode.value = element.value as ParticipantSortMode;
              }}
            >
              <option
                value="fullName"
                selected={sortMode.value === "fullName"}
              >
                По ФИО
              </option>
              <option
                value="organization"
                selected={sortMode.value === "organization"}
              >
                По учреждению
              </option>
            </select>
          </div>
        </div>

        <div class="mt-3 flex flex-wrap gap-2">
          <button
            type="button"
            class="rounded-lg bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500"
            onClick$={onApplyFilters$}
          >
            Найти
          </button>
          <button
            type="button"
            class="rounded-lg border border-border px-4 py-2 text-sm font-medium text-text hover:bg-surface focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500"
            onClick$={onResetFilters$}
          >
            Сбросить
          </button>
        </div>

        <div class="mt-4 overflow-hidden rounded-lg border border-border bg-surface">
          <div class="flex flex-wrap items-center justify-between gap-3 border-b border-border px-4 py-3">
            <label class="flex items-center gap-2 text-sm text-text">
              <input
                type="checkbox"
                checked={allVisibleSelected}
                disabled={selectableSubjectIds.length === 0}
                onChange$={(_, element) => {
                  selectedIds.value = element.checked
                    ? [...selectableSubjectIds]
                    : [];
                }}
              />
              <span>Выбрать всех доступных</span>
            </label>
            <p class="text-xs text-muted">
              Найдено {users.length} · доступно {selectableSubjectIds.length} ·
              выбрано {selectedCount}
            </p>
          </div>

          <div class="max-h-80 overflow-y-auto">
            {users.length === 0 ? (
              <p class="p-6 text-center text-sm text-muted">
                По текущему фильтру пользователи не найдены
              </p>
            ) : (
              users.map((user) => {
                const assigned = assignedSubjectIds.includes(user.subjectId);
                const checked = activeSelectedIds.includes(user.subjectId);
                const isCurrentUser = user.subjectId === currentUserId;

                return (
                  <label
                    key={user.subjectId}
                    class={[
                      "flex items-start gap-3 border-b border-border px-4 py-3 last:border-b-0",
                      assigned
                        ? "cursor-not-allowed bg-bg/70 opacity-70"
                        : "cursor-pointer hover:bg-bg/60",
                    ]}
                  >
                    <input
                      type="checkbox"
                      checked={checked}
                      disabled={assigned}
                      onChange$={(_, element) => {
                        selectedIds.value = toggleSelectedParticipant(
                          selectedIds.value.filter((subjectId) =>
                            selectableSubjectIds.includes(subjectId),
                          ),
                          user.subjectId,
                          element.checked,
                        );
                      }}
                    />
                    <span class="min-w-0 flex-1">
                      <span class="flex flex-wrap items-center gap-2">
                        <span class="text-sm font-semibold text-text">
                          {user.fullName}
                        </span>
                        {assigned && (
                          <span class="rounded-full bg-gray-100 px-2 py-0.5 text-xs text-gray-700 dark:bg-gray-800 dark:text-gray-200">
                            Уже участник
                          </span>
                        )}
                        {isCurrentUser && (
                          <span class="rounded-full bg-blue-100 px-2 py-0.5 text-xs font-medium text-blue-700 dark:bg-blue-900 dark:text-blue-100">
                            Вы
                          </span>
                        )}
                      </span>
                      <span class="mt-0.5 block text-xs text-muted">
                        {[user.organization, user.position]
                          .filter(Boolean)
                          .join(" · ")}
                      </span>
                    </span>
                  </label>
                );
              })
            )}
          </div>
        </div>

        <Form
          action={bulkAssignAction as never}
          class="mt-4 flex flex-col gap-3 rounded-lg border border-border bg-surface p-4 sm:flex-row sm:items-end sm:justify-between"
        >
          <input type="hidden" name="meetingId" value={meetingId} />
          <input type="hidden" name="defaultRole" value={bulkRole.value} />
          {activeSelectedIds.map((subjectId) => (
            <input
              key={subjectId}
              type="hidden"
              name="subjectIds[]"
              value={subjectId}
            />
          ))}

          <div class="min-w-48">
            <label
              class="mb-1 block text-xs font-medium text-muted"
              for="participant-bulk-role"
            >
              Роль для выбранных
            </label>
            <select
              id="participant-bulk-role"
              value={bulkRole.value}
              class="w-full rounded-lg border border-border bg-surface px-3 py-2 text-sm text-text focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500"
              onChange$={(_, element) => {
                bulkRole.value =
                  element.value as ParticipantAssignment["role"];
              }}
            >
              <option value="host" selected={bulkRole.value === "host"}>
                Организатор
              </option>
              <option
                value="moderator"
                selected={bulkRole.value === "moderator"}
              >
                Модератор
              </option>
              <option
                value="participant"
                selected={bulkRole.value === "participant"}
              >
                Участник
              </option>
            </select>
          </div>

          <div class="flex flex-col gap-2 sm:flex-row">
            <button
              type="button"
              class="rounded-lg border border-border px-4 py-2 text-sm font-medium text-text hover:bg-bg focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500"
              onClick$={() => {
                selectedIds.value = [];
              }}
              disabled={selectedCount === 0}
            >
              Снять выбор
            </button>
            <button
              type="submit"
              class="rounded-lg bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500 disabled:cursor-not-allowed disabled:opacity-50"
              disabled={selectedCount === 0 || isAssigning}
            >
              {isAssigning
                ? "Добавление..."
                : `Добавить (${selectedCount})`}
            </button>
          </div>
        </Form>
      </section>
    );
  },
);
