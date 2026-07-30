import { component$ } from "@qwik.dev/core";
import { buildAdminQueryHref } from "../admin-route-query";
import {
  buildIncidentQueueResetFiltersQueryUpdates,
  type IncidentQueueDerivedState,
  type IncidentQueueFilters,
} from "../admin-incidents.route-helpers";
import type { AdminIncidentList } from "../types";

interface AdminIncidentQueueFiltersProps {
  currentUrl: string;
  incidents: AdminIncidentList;
  filters: IncidentQueueFilters;
  state: IncidentQueueDerivedState;
}

const ENVIRONMENT_OPTIONS = ["", "dev", "test", "prod"] as const;
const PERIOD_OPTIONS = ["15m", "1h", "24h"] as const;
const CONTROL_CLASS =
  "rounded-xl border border-border bg-bg px-3 py-2 text-text";

export const AdminIncidentQueueFilters = component$(
  ({
    currentUrl,
    incidents,
    filters,
    state,
  }: AdminIncidentQueueFiltersProps) => (
    <details
      class="rounded-2xl border border-border bg-surface shadow-sm"
      open={state.advancedFiltersOpen}
    >
      <summary class="flex cursor-pointer list-none items-center justify-between gap-3 px-4 py-3 text-sm font-medium text-text sm:px-5">
        <span>Фильтры и точный поиск</span>
        <span class="text-xs font-normal text-muted">
          {state.advancedFiltersOpen ? "Есть уточнения" : "Расширить очередь"}
        </span>
      </summary>
      <div class="border-t border-border px-4 pb-4 pt-3 sm:px-5 sm:pb-5">
        <p class="text-sm text-muted">
          Trace/request и остальные поля уточняют выбранное представление.
        </p>
        <form
          method="get"
          class="mt-4 grid gap-3 sm:grid-cols-2 xl:grid-cols-3"
        >
          <input type="hidden" name="view" value={incidents.selectedView} />
          <input
            type="hidden"
            name="facet"
            value={incidents.selectedQuickFacet ?? ""}
          />
          <input type="hidden" name="limit" value={filters.limit} />
          <input type="hidden" name="offset" value="0" />
          <label class="flex flex-col gap-1 text-sm text-muted">
            Период
            <select
              class={CONTROL_CLASS}
              name="period"
              value={filters.period}
            >
              {PERIOD_OPTIONS.map((period) => (
                <option key={period} value={period}>
                  {period}
                </option>
              ))}
            </select>
          </label>
          <label class="flex flex-col gap-1 text-sm text-muted">
            Окружение
            <select
              class={CONTROL_CLASS}
              name="environment"
              value={filters.environment}
            >
              {ENVIRONMENT_OPTIONS.map((environment) => (
                <option key={environment || "all"} value={environment}>
                  {environment || "все"}
                </option>
              ))}
            </select>
          </label>
          <label class="flex flex-col gap-1 text-sm text-muted">
            Категория
            <input
              class={CONTROL_CLASS}
              name="category"
              value={filters.category}
            />
          </label>
          <label class="flex flex-col gap-1 text-sm text-muted">
            ID комнаты
            <input
              class={CONTROL_CLASS}
              name="roomId"
              value={filters.roomId}
            />
          </label>
          <label class="flex flex-col gap-1 text-sm text-muted">
            ID встречи
            <input
              class={CONTROL_CLASS}
              name="meetingId"
              value={filters.meetingId}
            />
          </label>
          <label class="flex flex-col gap-1 text-sm text-muted">
            ID субъекта / пользователя
            <input
              class={CONTROL_CLASS}
              name="subjectId"
              value={filters.subjectId}
            />
          </label>
          <label class="flex flex-col gap-1 text-sm text-muted">
            Trace ID
            <input
              class={CONTROL_CLASS}
              name="traceId"
              value={filters.traceId}
            />
          </label>
          <label class="flex flex-col gap-1 text-sm text-muted">
            Request ID
            <input
              class={CONTROL_CLASS}
              name="requestId"
              value={filters.requestId}
            />
          </label>
          <label class="flex flex-col gap-1 text-sm text-muted">
            Код ошибки
            <input
              class={CONTROL_CLASS}
              name="errorCode"
              value={filters.errorCode}
            />
          </label>
          <label class="flex flex-col gap-1 text-sm text-muted">
            Severity
            <input
              class={CONTROL_CLASS}
              name="severity"
              value={filters.severity}
            />
          </label>
          <label class="flex flex-col gap-1 text-sm text-muted">
            С
            <input class={CONTROL_CLASS} name="from" value={filters.from} />
          </label>
          <label class="flex flex-col gap-1 text-sm text-muted">
            По
            <input class={CONTROL_CLASS} name="to" value={filters.to} />
          </label>
          <div class="flex flex-wrap gap-2 sm:col-span-2 xl:col-span-3">
            <button
              type="submit"
              class="rounded-xl border border-text bg-text px-4 py-2 text-sm font-medium text-bg"
            >
              Применить
            </button>
            <a
              href={buildAdminQueryHref(
                new URL(currentUrl),
                buildIncidentQueueResetFiltersQueryUpdates(
                  filters,
                  incidents.selectedView,
                  incidents.selectedQuickFacet,
                ),
              )}
              class="rounded-xl border border-border px-4 py-2 text-sm text-text transition-colors hover:bg-surface-alt"
            >
              Сбросить уточнения
            </a>
          </div>
        </form>
      </div>
    </details>
  ),
);
