import { component$ } from "@qwik.dev/core";
import { RequestStatePanel } from "~/lib/shared";
import { buildAdminQueryHref } from "../admin-route-query";
import {
  buildAdminRoleHistoryPageQueryUpdates,
  buildAdminRoleHistoryResetQueryUpdates,
  type AdminRoleHistoryFilters,
} from "../admin-role-history.route-helpers";
import type { AdminRoleHistory } from "../types";
import { AdminRoleHistoryFilterForm } from "./AdminRoleHistoryFilterForm";
import { AdminRoleHistoryTimeline } from "./AdminRoleHistoryTimeline";

interface AdminRoleHistoryOverviewProps {
  currentUrl: string;
  history: AdminRoleHistory | null;
  hasPrimaryFilter: boolean;
  filters: AdminRoleHistoryFilters;
}

export const AdminRoleHistoryOverview = component$(
  ({
    currentUrl,
    history,
    hasPrimaryFilter,
    filters,
  }: AdminRoleHistoryOverviewProps) => {
    const url = new URL(currentUrl);
    const resetHref = buildAdminQueryHref(
      url,
      buildAdminRoleHistoryResetQueryUpdates(),
    );
    const previousHref =
      history && history.page > 0
        ? buildAdminQueryHref(
            url,
            buildAdminRoleHistoryPageQueryUpdates(history.page - 1),
          )
        : null;
    const nextHref =
      history && history.page + 1 < history.totalPages
        ? buildAdminQueryHref(
            url,
            buildAdminRoleHistoryPageQueryUpdates(history.page + 1),
          )
        : null;

    return (
      <div class="space-y-6">
        <section class="rounded-3xl border border-border bg-surface px-5 py-5 shadow-sm md:px-6">
          <div class="max-w-3xl">
            <p class="text-xs font-semibold uppercase tracking-[0.18em] text-primary">
              Контроль доступа
            </p>
            <h2 class="mt-2 text-2xl font-semibold tracking-tight text-text md:text-3xl">
              История ролей и назначений
            </h2>
            <p class="mt-2 text-sm leading-6 text-muted">
              Найдите изменения прав конкретного пользователя, комнаты или
              встречи. Запрос выполняется только после указания основного
              фильтра, чтобы не загружать неограниченный журнал.
            </p>
          </div>

          <AdminRoleHistoryFilterForm filters={filters} resetHref={resetHref} />
        </section>

        {!hasPrimaryFilter ? (
          <RequestStatePanel
            title="Сначала задайте область поиска"
            detail="Укажите пользователя, ID субъекта, комнаты или встречи. Дополнительные фильтры уточняют результат, но не заменяют область поиска."
          />
        ) : null}

        {history ? (
          <AdminRoleHistoryTimeline
            history={history}
            previousHref={previousHref}
            nextHref={nextHref}
          />
        ) : null}
      </div>
    );
  },
);
