import { component$ } from "@qwik.dev/core";
import { Form } from "@qwik.dev/router";
import { normalizeAdminConfigEnvironment } from "../admin-config.route-helpers";
import type {
  AdminConfigSetCapability,
  AdminConfigSetDetail,
} from "../admin-config.types";

interface ConfigActionState {
  isRunning: boolean;
}

interface AdminConfigSetOperationsProps {
  selectedConfig: AdminConfigSetDetail;
  capability: AdminConfigSetCapability;
  compatibilityAction: unknown;
  compatibilityActionState: ConfigActionState;
  rolloutAction: unknown;
  rolloutActionState: ConfigActionState;
  rollbackAction: unknown;
  rollbackActionState: ConfigActionState;
}

export const AdminConfigSetOperations = component$(
  ({
    selectedConfig,
    capability,
    compatibilityAction,
    compatibilityActionState,
    rolloutAction,
    rolloutActionState,
    rollbackAction,
    rollbackActionState,
  }: AdminConfigSetOperationsProps) => {
    const rolloutBlocked =
      selectedConfig.compatibility?.status === "INCOMPATIBLE";

    return (
      <section class="rounded-3xl border border-border bg-surface p-4 shadow-sm sm:p-5">
        <div>
          <p class="text-xs uppercase tracking-[0.18em] text-muted">
            Операции
          </p>
          <h3 class="mt-1 text-lg font-semibold text-text">
            Проверка и публикация
          </h3>
        </div>

        <div class="mt-4 grid gap-3 lg:grid-cols-2">
          <div class="rounded-2xl border border-border bg-bg p-4">
            <div class="flex items-start justify-between gap-3">
              <div>
                <p class="text-sm font-semibold text-text">Совместимость</p>
                <p class="mt-1 text-xs text-muted">
                  {selectedConfig.compatibility?.status ?? "Не проверялась"}
                </p>
              </div>
              {selectedConfig.compatibility?.traceId ? (
                <span class="max-w-40 truncate text-xs text-muted">
                  {selectedConfig.compatibility.traceId}
                </span>
              ) : null}
            </div>
            {selectedConfig.compatibility?.mismatches.length ? (
              <ul class="mt-3 space-y-1 text-xs text-muted">
                {selectedConfig.compatibility.mismatches.map(
                  (mismatch, index) => (
                    <li key={`${mismatch.code ?? "mismatch"}-${index}`}>
                      {mismatch.code ?? "MISMATCH"}:{" "}
                      {mismatch.message ?? "Без описания"}
                    </li>
                  ),
                )}
              </ul>
            ) : null}
            <Form action={compatibilityAction as never} class="mt-4">
              <input
                type="hidden"
                name="configSetId"
                value={selectedConfig.configSetId}
              />
              <button
                type="submit"
                disabled={compatibilityActionState.isRunning}
                class="rounded-xl border border-border px-3 py-2 text-sm text-text disabled:cursor-not-allowed disabled:opacity-50"
              >
                {compatibilityActionState.isRunning
                  ? "Проверяем..."
                  : "Проверить совместимость"}
              </button>
            </Form>
          </div>

          <div class="rounded-2xl border border-border bg-bg p-4">
            <div class="flex items-start justify-between gap-3">
              <div>
                <p class="text-sm font-semibold text-text">Развёртывание</p>
                <p class="mt-1 text-xs text-muted">
                  {selectedConfig.latestRollout?.status ?? "NO_ROLLOUT"}
                </p>
              </div>
              <span class="text-xs text-muted">
                {selectedConfig.latestRollout?.actorId ?? "Оператор не указан"}
              </span>
            </div>
            {selectedConfig.latestRollout?.validationErrors ? (
              <p class="mt-3 text-xs text-muted">
                {selectedConfig.latestRollout.validationErrors}
              </p>
            ) : null}
            <div class="mt-4 flex flex-wrap gap-2">
              <Form action={rolloutAction as never}>
                <input
                  type="hidden"
                  name="configSetId"
                  value={selectedConfig.configSetId}
                />
                <button
                  type="submit"
                  disabled={
                    !capability.canMutate ||
                    rolloutBlocked ||
                    rolloutActionState.isRunning
                  }
                  class="rounded-xl border border-text bg-text px-3 py-2 text-sm font-medium text-bg disabled:cursor-not-allowed disabled:opacity-50"
                >
                  {rolloutActionState.isRunning
                    ? "Запуск..."
                    : "Запустить развёртывание"}
                </button>
              </Form>
              <Form action={rollbackAction as never}>
                <input
                  type="hidden"
                  name="configSetId"
                  value={selectedConfig.configSetId}
                />
                <input
                  type="hidden"
                  name="environmentType"
                  value={normalizeAdminConfigEnvironment(
                    selectedConfig.environmentType,
                  )}
                />
                <button
                  type="submit"
                  disabled={
                    !capability.canMutate || rollbackActionState.isRunning
                  }
                  class="rounded-xl border border-border px-3 py-2 text-sm text-text disabled:cursor-not-allowed disabled:opacity-50"
                >
                  {rollbackActionState.isRunning
                    ? "Откат..."
                    : "Выполнить откат"}
                </button>
              </Form>
            </div>
            {rolloutBlocked ? (
              <p class="mt-3 text-xs text-muted">
                Развёртывание заблокировано результатом INCOMPATIBLE.
              </p>
            ) : null}
          </div>
        </div>
      </section>
    );
  },
);
