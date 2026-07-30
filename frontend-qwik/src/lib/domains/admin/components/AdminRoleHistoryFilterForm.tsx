import { component$ } from "@qwik.dev/core";
import type { AdminRoleHistoryFilters } from "../admin-role-history.route-helpers";
import { hasAdminRoleHistoryAdvancedFilters } from "../admin-role-history.presentation";

interface AdminRoleHistoryFilterFormProps {
  filters: AdminRoleHistoryFilters;
  resetHref: string;
}

const inputClass =
  "w-full rounded-xl border border-border bg-bg px-3 py-2.5 text-sm text-text shadow-sm transition focus-visible:border-primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/20";
const labelClass = "flex flex-col gap-1.5 text-sm font-medium text-text";

export const AdminRoleHistoryFilterForm = component$(
  ({ filters, resetHref }: AdminRoleHistoryFilterFormProps) => {
    const advancedFiltersOpen = hasAdminRoleHistoryAdvancedFilters(filters);

    return (
      <form
        method="get"
        class="mt-5 space-y-5"
        aria-label="Фильтры истории ролей"
      >
        {filters.returnTo ? (
          <input type="hidden" name="returnTo" value={filters.returnTo} />
        ) : null}

        <div class="grid gap-4 md:grid-cols-2">
          <label class={labelClass}>
            Пользователь
            <input
              class={inputClass}
              type="search"
              name="q"
              value={filters.q}
              placeholder="ФИО или идентификатор"
              autocomplete="off"
            />
          </label>
          <label class={labelClass}>
            ID субъекта
            <input
              class={inputClass}
              type="text"
              name="subjectId"
              value={filters.subjectId}
              placeholder="Точный идентификатор пользователя"
              autocomplete="off"
            />
          </label>
          <label class={labelClass}>
            ID комнаты
            <input
              class={inputClass}
              type="text"
              name="roomId"
              value={filters.roomId}
              placeholder="Ограничить одной комнатой"
              autocomplete="off"
            />
          </label>
          <label class={labelClass}>
            ID встречи
            <input
              class={inputClass}
              type="text"
              name="meetingId"
              value={filters.meetingId}
              placeholder="Ограничить одной встречей"
              autocomplete="off"
            />
          </label>
        </div>

        <details
          class="rounded-2xl border border-border bg-bg/70"
          open={advancedFiltersOpen}
        >
          <summary class="cursor-pointer select-none px-4 py-3 text-sm font-semibold text-text">
            Дополнительные фильтры
            <span class="ml-2 font-normal text-muted">
              окружение, действие, роль, оператор и период
            </span>
          </summary>
          <div class="grid gap-4 border-t border-border px-4 py-4 md:grid-cols-2 xl:grid-cols-3">
            <label class={labelClass}>
              Окружение
              <select class={inputClass} name="environment">
                <option value="" selected={!filters.environment}>
                  Все окружения
                </option>
                <option value="dev" selected={filters.environment === "dev"}>
                  Разработка
                </option>
                <option value="test" selected={filters.environment === "test"}>
                  Тест
                </option>
                <option value="prod" selected={filters.environment === "prod"}>
                  Рабочая среда
                </option>
              </select>
            </label>
            <label class={labelClass}>
              Тип действия
              <select class={inputClass} name="actionType">
                <option value="" selected={!filters.actionType}>
                  Все действия
                </option>
                <option
                  value="assign"
                  selected={filters.actionType === "assign"}
                >
                  Назначение
                </option>
                <option
                  value="update"
                  selected={filters.actionType === "update"}
                >
                  Изменение роли
                </option>
                <option
                  value="unassign"
                  selected={filters.actionType === "unassign"}
                >
                  Отзыв назначения
                </option>
              </select>
            </label>
            <label class={labelClass}>
              Роль
              <select class={inputClass} name="role">
                <option value="" selected={!filters.role}>
                  Все роли
                </option>
                <option value="host" selected={filters.role === "host"}>
                  Организатор
                </option>
                <option
                  value="moderator"
                  selected={filters.role === "moderator"}
                >
                  Модератор
                </option>
                <option
                  value="participant"
                  selected={filters.role === "participant"}
                >
                  Участник
                </option>
              </select>
            </label>
            <label class={labelClass}>
              ID оператора
              <input
                class={inputClass}
                type="text"
                name="actorId"
                value={filters.actorId}
                autocomplete="off"
              />
            </label>
            <label class={labelClass}>
              Начало периода, UTC
              <input
                class={inputClass}
                type="text"
                name="from"
                value={filters.from}
                placeholder="2026-03-19T09:00:00Z"
                inputMode="text"
              />
            </label>
            <label class={labelClass}>
              Конец периода, UTC
              <input
                class={inputClass}
                type="text"
                name="to"
                value={filters.to}
                placeholder="2026-03-19T10:00:00Z"
                inputMode="text"
              />
            </label>
            <label class={labelClass}>
              Событий на странице
              <select class={inputClass} name="pageSize">
                {[20, 50, 100].map((size) => (
                  <option
                    key={String(size)}
                    value={String(size)}
                    selected={filters.pageSize === String(size)}
                  >
                    {String(size)}
                  </option>
                ))}
              </select>
            </label>
          </div>
        </details>

        <input type="hidden" name="page" value="0" />
        <div class="flex flex-wrap gap-3">
          <button
            type="submit"
            class="rounded-xl bg-primary px-4 py-2.5 text-sm font-semibold text-primary-foreground shadow-sm transition hover:brightness-95"
          >
            Показать историю
          </button>
          <a
            href={resetHref}
            class="rounded-xl border border-border bg-surface px-4 py-2.5 text-sm font-medium text-text transition hover:bg-surface-2"
          >
            Сбросить фильтры
          </a>
        </div>
      </form>
    );
  },
);
