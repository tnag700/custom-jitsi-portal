import { component$ } from "@qwik.dev/core";
import { RequestStatePanel } from "~/lib/shared";
import { buildAdminQueryHref } from "../admin-route-query";
import type { AdminConfigSetSummary } from "../admin-config.types";

interface AdminConfigSetListProps {
  currentUrl: string;
  items: AdminConfigSetSummary[];
  selectedConfigId: string;
  createMode: boolean;
}

export const AdminConfigSetList = component$(
  ({
    currentUrl,
    items,
    selectedConfigId,
    createMode,
  }: AdminConfigSetListProps) => (
    <section class="rounded-3xl border border-border bg-surface p-4 shadow-sm sm:p-5">
      <div class="flex items-center justify-between gap-3">
        <div>
          <p class="text-xs uppercase tracking-[0.18em] text-muted">
            Каталог
          </p>
          <h3 class="mt-1 text-lg font-semibold text-text">Наборы</h3>
        </div>
        <p class="text-sm text-muted">{String(items.length)} найдено</p>
      </div>

      {items.length > 0 ? (
        <nav aria-label="Список конфиг-наборов" class="mt-4 space-y-2">
          {items.map((item) => {
            const active =
              item.configSetId === selectedConfigId && !createMode;
            return (
              <a
                key={item.configSetId}
                href={buildAdminQueryHref(new URL(currentUrl), {
                  configSetId: item.configSetId,
                  mode: null,
                })}
                aria-current={active ? "page" : undefined}
                class={[
                  "block rounded-2xl border px-4 py-3 transition-colors",
                  active
                    ? "border-text bg-bg"
                    : "border-border bg-bg hover:bg-surface-alt",
                ]}
              >
                <div class="flex items-start justify-between gap-3">
                  <div class="min-w-0">
                    <p class="truncate text-sm font-semibold text-text">
                      {item.name}
                    </p>
                    <p class="mt-1 text-xs text-muted">
                      {item.environmentType} · {item.status}
                    </p>
                  </div>
                  <span class="shrink-0 rounded-full border border-border px-2 py-1 text-xs text-muted">
                    {item.latestRollout?.status ?? "NO_ROLLOUT"}
                  </span>
                </div>
                <div class="mt-3 flex flex-wrap gap-2 text-xs text-muted">
                  <span>
                    Совместимость:{" "}
                    {item.compatibilityStatus ?? "не проверялась"}
                  </span>
                  <span>·</span>
                  <span>Обновлён {item.updatedAt}</span>
                </div>
              </a>
            );
          })}
        </nav>
      ) : (
        <div class="mt-4">
          <RequestStatePanel
            title="Конфиг-наборы не найдены"
            detail="Измените фильтры или создайте новый набор."
          />
        </div>
      )}
    </section>
  ),
);
