import { component$ } from "@qwik.dev/core";
import { Form } from "@qwik.dev/router";
import { RequestStatePanel } from "~/lib/shared";
import { buildAdminQueryHref } from "../admin-route-query";
import { normalizeAdminConfigEnvironment } from "../admin-config.route-helpers";
import type {
  AdminConfigSetCapability,
  AdminConfigSetDetail,
} from "../admin-config.types";

interface ConfigActionState {
  isRunning: boolean;
}

interface AdminConfigSetEditorProps {
  currentUrl: string;
  selectedConfig: AdminConfigSetDetail | null;
  capability: AdminConfigSetCapability;
  mode: string;
  saveAction: unknown;
  saveActionState: ConfigActionState;
}

const CONTROL_CLASS =
  "rounded-xl border border-border bg-bg px-3 py-2 text-text";
const ENVIRONMENTS = ["DEV", "TEST", "PROD"] as const;

export const AdminConfigSetEditor = component$(
  ({
    currentUrl,
    selectedConfig,
    capability,
    mode,
    saveAction,
    saveActionState,
  }: AdminConfigSetEditorProps) => {
    const createMode = mode === "create";
    const editMode = mode === "edit";
    const formMode = createMode || editMode;
    const title = createMode
      ? "Новый конфиг-набор"
      : editMode
        ? "Редактирование набора"
        : "Карточка конфиг-набора";

    return (
      <section class="rounded-3xl border border-border bg-surface p-4 shadow-sm sm:p-5">
        <div class="flex flex-wrap items-center justify-between gap-3">
          <div>
            <p class="text-xs uppercase tracking-[0.18em] text-muted">
              Рабочая область
            </p>
            <h3 class="mt-1 text-lg font-semibold text-text">{title}</h3>
          </div>
          {!formMode && selectedConfig ? (
            <a
              href={buildAdminQueryHref(new URL(currentUrl), {
                mode: "edit",
                configSetId: selectedConfig.configSetId,
              })}
              class="rounded-xl border border-border px-3 py-2 text-sm font-medium text-text transition-colors hover:bg-surface-alt"
            >
              Редактировать
            </a>
          ) : formMode ? (
            <a
              href={buildAdminQueryHref(new URL(currentUrl), {
                mode: null,
                configSetId: createMode
                  ? null
                  : (selectedConfig?.configSetId ?? null),
              })}
              class="text-sm text-muted underline"
            >
              Отменить
            </a>
          ) : null}
        </div>

        {formMode && (createMode || selectedConfig) ? (
          <Form
            action={saveAction as never}
            class="mt-4 grid gap-3 sm:grid-cols-2"
          >
            <input
              type="hidden"
              name="mode"
              value={createMode ? "create" : "update"}
            />
            {!createMode && selectedConfig ? (
              <input
                type="hidden"
                name="configSetId"
                value={selectedConfig.configSetId}
              />
            ) : null}

            <label class="flex flex-col gap-1 text-sm text-muted sm:col-span-2">
              Название
              <input
                class={CONTROL_CLASS}
                name="name"
                value={selectedConfig?.name ?? ""}
                required
              />
            </label>
            <label class="flex flex-col gap-1 text-sm text-muted">
              Окружение
              <select
                class={CONTROL_CLASS}
                name="environmentType"
                value={normalizeAdminConfigEnvironment(
                  selectedConfig?.environmentType ?? "DEV",
                )}
              >
                {ENVIRONMENTS.map((environment) => (
                  <option key={environment} value={environment}>
                    {environment}
                  </option>
                ))}
              </select>
            </label>
            <label class="flex flex-col gap-1 text-sm text-muted">
              Алгоритм
              <input
                class={CONTROL_CLASS}
                name="algorithm"
                value={selectedConfig?.algorithm ?? "HS256"}
                required
              />
            </label>
            <label class="flex flex-col gap-1 text-sm text-muted">
              Issuer
              <input
                class={CONTROL_CLASS}
                name="issuer"
                value={selectedConfig?.issuer ?? ""}
                required
              />
            </label>
            <label class="flex flex-col gap-1 text-sm text-muted">
              Audience
              <input
                class={CONTROL_CLASS}
                name="audience"
                value={selectedConfig?.audience ?? ""}
                required
              />
            </label>
            <label class="flex flex-col gap-1 text-sm text-muted">
              Claim роли
              <input
                class={CONTROL_CLASS}
                name="roleClaim"
                value={selectedConfig?.roleClaim ?? ""}
              />
            </label>
            <label class="flex flex-col gap-1 text-sm text-muted">
              Секрет подписи
              <input
                class={CONTROL_CLASS}
                type="password"
                autoComplete="new-password"
                name="signingSecret"
                value=""
                placeholder={
                  selectedConfig?.signingSecret
                    ? "Оставьте пустым, чтобы не менять secret"
                    : "secret"
                }
              />
            </label>
            <label class="flex flex-col gap-1 text-sm text-muted">
              JWKS URI
              <input
                class={CONTROL_CLASS}
                name="jwksUri"
                value={selectedConfig?.jwksUri ?? ""}
              />
            </label>
            <label class="flex flex-col gap-1 text-sm text-muted">
              Access token, мин.
              <input
                class={CONTROL_CLASS}
                type="number"
                min="1"
                name="accessTtlMinutes"
                value={String(selectedConfig?.accessTtlMinutes ?? 15)}
                required
              />
            </label>
            <label class="flex flex-col gap-1 text-sm text-muted">
              Refresh token, мин.
              <input
                class={CONTROL_CLASS}
                type="number"
                min="1"
                name="refreshTtlMinutes"
                value={String(selectedConfig?.refreshTtlMinutes ?? 60)}
              />
            </label>
            <label class="flex flex-col gap-1 text-sm text-muted sm:col-span-2">
              URL сервиса встреч
              <input
                class={CONTROL_CLASS}
                type="url"
                name="meetingsServiceUrl"
                value={selectedConfig?.meetingsServiceUrl ?? "https://"}
                required
              />
            </label>
            <div class="flex flex-wrap items-center gap-2 sm:col-span-2">
              <button
                type="submit"
                disabled={
                  !capability.canMutate || saveActionState.isRunning
                }
                class="rounded-xl border border-text bg-text px-4 py-2 text-sm font-medium text-bg disabled:cursor-not-allowed disabled:opacity-50"
              >
                {saveActionState.isRunning
                  ? "Сохранение..."
                  : createMode
                    ? "Создать набор"
                    : "Сохранить изменения"}
              </button>
              {!capability.canMutate ? (
                <span class="text-xs text-muted">
                  Изменения недоступны для роли {capability.role}.
                </span>
              ) : null}
            </div>
          </Form>
        ) : selectedConfig ? (
          <dl class="mt-4 grid gap-3 sm:grid-cols-2">
            {[
              ["Название", selectedConfig.name],
              ["Окружение", selectedConfig.environmentType],
              ["Статус", selectedConfig.status],
              ["Алгоритм", selectedConfig.algorithm],
              ["Issuer", selectedConfig.issuer],
              ["Audience", selectedConfig.audience],
              ["Claim роли", selectedConfig.roleClaim || "не задан"],
              [
                "Access token",
                `${String(selectedConfig.accessTtlMinutes)} мин.`,
              ],
              [
                "Refresh token",
                `${String(selectedConfig.refreshTtlMinutes ?? "—")} мин.`,
              ],
              ["URL встреч", selectedConfig.meetingsServiceUrl],
              ["Обновлён", selectedConfig.updatedAt],
            ].map(([label, value]) => (
              <div
                key={label}
                class="min-w-0 rounded-2xl border border-border bg-bg px-3 py-2.5"
              >
                <dt class="text-xs text-muted">{label}</dt>
                <dd class="mt-1 break-words text-sm font-medium text-text">
                  {value}
                </dd>
              </div>
            ))}
          </dl>
        ) : (
          <div class="mt-4">
            <RequestStatePanel
              title="Выберите конфиг-набор"
              detail="Откройте карточку из списка или создайте новый набор."
            />
          </div>
        )}
      </section>
    );
  },
);
