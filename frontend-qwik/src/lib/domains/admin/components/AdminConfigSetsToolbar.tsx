import { component$ } from "@qwik.dev/core";
import { buildAdminQueryHref } from "../admin-route-query";
import {
  type AdminConfigRouteFilters,
} from "../admin-config.route-helpers";
import { resolveIncidentReturnTo } from "../admin-incidents.route-helpers";
import type { AdminConfigSetCapability } from "../admin-config.types";

interface AdminConfigSetsToolbarProps {
  currentUrl: string;
  capability: AdminConfigSetCapability;
  filters: AdminConfigRouteFilters;
}

const ENVIRONMENT_OPTIONS = ["", "DEV", "TEST", "PROD"] as const;
const CONTROL_CLASS =
  "rounded-xl border border-border bg-bg px-3 py-2 text-text";

export const AdminConfigSetsToolbar = component$(
  ({ currentUrl, capability, filters }: AdminConfigSetsToolbarProps) => {
    const url = new URL(currentUrl);
    const returnHref = filters.returnTo
      ? resolveIncidentReturnTo(url, filters.environment)
      : null;

    return (
      <section class="rounded-3xl border border-border bg-surface p-4 shadow-sm sm:p-5">
        <div class="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
          <div>
            {returnHref ? (
              <a
                href={returnHref}
                class="mb-2 inline-flex text-sm text-muted underline"
              >
                Вернуться к инцидентам
              </a>
            ) : null}
            <p class="text-xs uppercase tracking-[0.22em] text-muted">
              Управление конфигурацией
            </p>
            <h2 class="mt-1.5 text-xl font-semibold text-text">
              Конфиг-наборы
            </h2>
            <p class="mt-1.5 max-w-2xl text-sm text-muted">
              Проверяйте совместимость и управляйте активной конфигурацией
              окружений.
            </p>
          </div>

          <div class="flex flex-wrap items-center gap-2">
            <span class="rounded-full border border-border bg-bg px-3 py-1.5 text-sm text-text">
              {`Роль ${capability.role}`}
            </span>
            <span
              class={
                capability.canMutate
                  ? "rounded-full bg-text px-3 py-1.5 text-sm font-medium text-bg"
                  : "rounded-full border border-border px-3 py-1.5 text-sm text-muted"
              }
            >
              {capability.canMutate ? "Управление" : "Только чтение"}
            </span>
            <a
              href={buildAdminQueryHref(url, {
                mode: "create",
                configSetId: null,
              })}
              class="rounded-xl border border-text bg-text px-3 py-2 text-sm font-medium text-bg"
            >
              Новый набор
            </a>
          </div>
        </div>

        <details
          class="mt-4 rounded-2xl border border-border bg-bg/50"
          open={Boolean(filters.environment || filters.status)}
        >
          <summary class="flex cursor-pointer list-none items-center justify-between gap-3 px-4 py-3 text-sm font-medium text-text">
            <span>Фильтры списка</span>
            <span class="text-xs font-normal text-muted">
              {filters.environment || filters.status
                ? "Есть активные фильтры"
                : "Все окружения и статусы"}
            </span>
          </summary>
          <form
            method="get"
            class="grid gap-3 border-t border-border px-4 pb-4 pt-3 sm:grid-cols-2 lg:grid-cols-[1fr_1fr_auto]"
          >
            {filters.returnTo ? (
              <input type="hidden" name="returnTo" value={filters.returnTo} />
            ) : null}
            <label class="flex flex-col gap-1 text-sm text-muted">
              Окружение
              <select
                class={CONTROL_CLASS}
                name="environment"
                value={filters.environment}
              >
                {ENVIRONMENT_OPTIONS.map((environment) => (
                  <option
                    key={environment || "all"}
                    value={environment}
                    selected={environment === filters.environment}
                  >
                    {environment || "все"}
                  </option>
                ))}
              </select>
            </label>
            <label class="flex flex-col gap-1 text-sm text-muted">
              Статус
              <input
                class={CONTROL_CLASS}
                type="text"
                name="status"
                value={filters.status}
                placeholder="active"
              />
            </label>
            <div class="flex items-end gap-2">
              <button
                type="submit"
                class="rounded-xl border border-text bg-text px-4 py-2 text-sm font-medium text-bg"
              >
                Применить
              </button>
              <a
                href={buildAdminQueryHref(url, {
                  environment: null,
                  status: null,
                  configSetId: null,
                  mode: null,
                })}
                class="rounded-xl border border-border px-4 py-2 text-sm text-text transition-colors hover:bg-surface-alt"
              >
                Сбросить
              </a>
            </div>
          </form>
        </details>

        {!capability.canMutate && capability.reason ? (
          <p class="mt-3 text-xs text-muted">{capability.reason}</p>
        ) : null}
      </section>
    );
  },
);
