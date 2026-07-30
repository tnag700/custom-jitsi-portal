import { component$ } from "@qwik.dev/core";
import { ApiErrorAlert } from "~/lib/shared";
import type { ProblemDetail } from "~/lib/shared";
import type { AdminConfigRouteFilters } from "../admin-config.route-helpers";
import type {
  AdminConfigOperationResult,
  AdminConfigSetCapability,
  AdminConfigSetDetail,
  AdminConfigSetSummary,
} from "../admin-config.types";
import { AdminConfigSetEditor } from "./AdminConfigSetEditor";
import { AdminConfigSetList } from "./AdminConfigSetList";
import { AdminConfigSetOperations } from "./AdminConfigSetOperations";
import { AdminConfigSetsToolbar } from "./AdminConfigSetsToolbar";

interface ConfigActionState {
  isRunning: boolean;
}

interface AdminConfigSetsOverviewProps {
  currentUrl: string;
  items: AdminConfigSetSummary[];
  selectedConfig: AdminConfigSetDetail | null;
  capability: AdminConfigSetCapability;
  filters: AdminConfigRouteFilters;
  saveAction: unknown;
  compatibilityAction: unknown;
  rolloutAction: unknown;
  rollbackAction: unknown;
  activeOperation: AdminConfigOperationResult | null;
  activeError: ProblemDetail | null;
}

function enrichSelectedSummary(
  items: AdminConfigSetSummary[],
  selectedConfig: AdminConfigSetDetail | null,
): AdminConfigSetSummary[] {
  if (!selectedConfig) {
    return items;
  }
  return items.map((item) =>
    item.configSetId === selectedConfig.configSetId
      ? {
          ...item,
          latestRollout:
            selectedConfig.latestRollout ?? item.latestRollout,
          compatibilityStatus:
            selectedConfig.compatibility?.status ??
            item.compatibilityStatus,
          compatibilityTraceId:
            selectedConfig.compatibility?.traceId ??
            item.compatibilityTraceId,
        }
      : item,
  );
}

function asActionState(action: unknown): ConfigActionState {
  if (
    action &&
    typeof action === "object" &&
    "isRunning" in action &&
    typeof action.isRunning === "boolean"
  ) {
    return { isRunning: action.isRunning };
  }
  return { isRunning: false };
}

export const AdminConfigSetsOverview = component$(
  ({
    currentUrl,
    items,
    selectedConfig,
    capability,
    filters,
    saveAction,
    compatibilityAction,
    rolloutAction,
    rollbackAction,
    activeOperation,
    activeError,
  }: AdminConfigSetsOverviewProps) => {
    const createMode = filters.mode === "create";
    const formMode = createMode || filters.mode === "edit";
    const visibleItems = enrichSelectedSummary(items, selectedConfig);

    return (
      <div class="space-y-4 md:space-y-5">
        <AdminConfigSetsToolbar
          currentUrl={currentUrl}
          capability={capability}
          filters={filters}
        />

        {activeError ? (
          <ApiErrorAlert
            title={activeError.title ?? "Ошибка"}
            message={activeError.detail ?? "Неизвестная ошибка"}
            errorCode={activeError.errorCode ?? "ADMIN_CONFIG_UNKNOWN"}
            traceId={activeError.traceId}
          />
        ) : null}

        {activeOperation ? (
          <section class="rounded-2xl border border-border bg-surface px-4 py-3 shadow-sm">
            <div class="flex flex-wrap items-center justify-between gap-2">
              <div>
                <p class="text-xs uppercase tracking-[0.18em] text-muted">
                  Последняя операция
                </p>
                <p class="mt-1 text-sm font-medium text-text">
                  {activeOperation.message}
                </p>
              </div>
              <div class="text-right text-xs text-muted">
                <p>{activeOperation.status}</p>
                <p>
                  {activeOperation.actorId ?? "n/a"}
                  {activeOperation.traceId
                    ? ` · ${activeOperation.traceId}`
                    : ""}
                </p>
              </div>
            </div>
          </section>
        ) : null}

        <section class="grid gap-4 xl:grid-cols-[minmax(18rem,0.75fr)_minmax(0,1.25fr)]">
          <AdminConfigSetList
            currentUrl={currentUrl}
            items={visibleItems}
            selectedConfigId={filters.configSetId}
            createMode={createMode}
          />
          <div
            class={[
              "space-y-4",
              formMode ? "order-first xl:order-none" : "",
            ]}
          >
            <AdminConfigSetEditor
              currentUrl={currentUrl}
              selectedConfig={selectedConfig}
              capability={capability}
              mode={filters.mode}
              saveAction={saveAction}
              saveActionState={asActionState(saveAction)}
            />
            {selectedConfig && !createMode ? (
              <AdminConfigSetOperations
                selectedConfig={selectedConfig}
                capability={capability}
                compatibilityAction={compatibilityAction}
                compatibilityActionState={asActionState(
                  compatibilityAction,
                )}
                rolloutAction={rolloutAction}
                rolloutActionState={asActionState(rolloutAction)}
                rollbackAction={rollbackAction}
                rollbackActionState={asActionState(rollbackAction)}
              />
            ) : null}
          </div>
        </section>
      </div>
    );
  },
);
