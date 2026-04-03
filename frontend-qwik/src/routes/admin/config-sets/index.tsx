import { component$ } from "@qwik.dev/core";
import { Form, routeAction$, routeLoader$, useLocation, z, zod$, type DocumentHead } from "@qwik.dev/router";
import { resolveAuthRecoveryRedirectPath, type SafeUserProfile } from "~/lib/domains/auth";
import {
  AdminConfigServiceError,
  adminConfigSetFormSchema,
  buildAdminConfigRouteFilters,
  buildAdminOverviewHref as buildOverviewHref,
  buildAdminQueryHref as buildQueryHref,
  checkAdminConfigSetCompatibility,
  createAdminConfigSet,
  fetchAdminConfigSet,
  fetchAdminConfigSets,
  filterAdminConfigSummaries,
  loadAdminConfigLatestRollouts,
  normalizeAdminConfigEnvironment,
  resolveIncidentReturnTo,
  resolveAdminConfigCapability,
  resolveAdminConfigSelectedId,
  rollbackAdminConfigSet,
  rolloutAdminConfigSet,
  shouldLoadAdminConfigDetail,
  updateAdminConfigSet,
  type AdminConfigOperationResult,
  type AdminConfigSetCapability,
  type AdminConfigSetDetail,
  type AdminConfigSetSummary,
} from "~/lib/domains/admin";
import { ApiErrorAlert, RequestStatePanel, type ProblemDetail } from "~/lib/shared";
import { buildMutationRequestContext, buildServerRequestContext } from "~/lib/shared/routes/server-handlers";

interface ConfigSetsLoaderData {
  items: AdminConfigSetSummary[];
  selectedConfig: AdminConfigSetDetail | null;
  capability: AdminConfigSetCapability;
  loadError: ProblemDetail | null;
  filters: {
    environment: string;
    status: string;
    mode: string;
    configSetId: string;
    returnTo: string;
  };
}
const ENVIRONMENT_OPTIONS = ["", "DEV", "TEST", "PROD"] as const;

export const useAdminConfigSets = routeLoader$(async ({ sharedMap, cookie, query, redirect, url }) => {
  const user = (sharedMap.get("user") as SafeUserProfile | null) ?? null;
  const returnTo = `${url.pathname}${url.search}`;
  if (!user) {
    throw redirect(302, resolveAuthRecoveryRedirectPath(undefined, returnTo));
  }

  const requestContext = buildServerRequestContext({ sharedMap, cookie });
  const capability = resolveAdminConfigCapability(user);
  const filters = buildAdminConfigRouteFilters(query);

  try {
    const page = await fetchAdminConfigSets(requestContext, {
      tenantId: user.tenant,
      page: 0,
      size: 20,
    });
    const rolloutByEnvironment = await loadAdminConfigLatestRollouts(requestContext, user.tenant, page.items);
    const items = filterAdminConfigSummaries(
      page.items.map((item) => ({
        ...item,
        latestRollout: rolloutByEnvironment.get(normalizeAdminConfigEnvironment(item.environmentType)) ?? null,
        capability,
      })),
      filters,
    );

    const selectedConfigId = resolveAdminConfigSelectedId(filters, items);
    const selectedConfig = shouldLoadAdminConfigDetail(filters, selectedConfigId)
      ? await fetchAdminConfigSet(requestContext, {
          configSetId: selectedConfigId,
          tenantId: user.tenant,
        })
      : null;

    return {
      items,
      selectedConfig,
      capability,
      loadError: null,
      filters: {
        ...filters,
        configSetId: selectedConfigId,
      },
    } satisfies ConfigSetsLoaderData;
  } catch (error) {
    if (error instanceof AdminConfigServiceError) {
      if (error.payload.errorCode === "AUTH_REQUIRED") {
        throw redirect(302, resolveAuthRecoveryRedirectPath(error, returnTo));
      }
      if (error.payload.errorCode === "ACCESS_DENIED") {
        throw redirect(302, "/");
      }
      return {
        items: [],
        selectedConfig: null,
        capability,
        loadError: error.payload,
        filters,
      } satisfies ConfigSetsLoaderData;
    }

    return {
      items: [],
      selectedConfig: null,
      capability,
      loadError: {
        title: "Ошибка загрузки",
        detail: "Не удалось загрузить раздел управления конфигурацией.",
        errorCode: "ADMIN_CONFIG_UI_UNAVAILABLE",
      },
      filters,
    } satisfies ConfigSetsLoaderData;
  }
});

export const useSaveConfigSet = routeAction$(
  async (data, { sharedMap, cookie, fail, redirect, url }) => {
    const user = sharedMap.get("user") as SafeUserProfile;
    const requestContext = await buildMutationRequestContext({ sharedMap, cookie });
    const returnTo = `${url.pathname}${url.search}`;
    const payload = {
      ...data,
      roleClaim: data.roleClaim?.trim() || undefined,
      signingSecret: data.signingSecret?.trim() || undefined,
      jwksUri: data.jwksUri?.trim() || undefined,
      tenantId: user.tenant,
    };

    try {
      const configSetId = data.configSetId;
      if (data.mode === "update" && !configSetId) {
        return fail(400, {
          error: {
            title: "Ошибка сохранения",
            detail: "configSetId обязателен для обновления.",
            errorCode: "ADMIN_CONFIG_ID_REQUIRED",
          },
        });
      }
      const saved = data.mode === "create"
        ? await createAdminConfigSet(requestContext, payload)
        : await updateAdminConfigSet(requestContext, configSetId as string, payload);
      return {
        success: true as const,
        configSet: saved,
        operation: {
          kind: "save",
          status: saved.status,
          message: data.mode === "create" ? "Конфиг-набор создан." : "Конфиг-набор обновлён.",
          traceId: null,
          actorId: user.displayName,
        } satisfies AdminConfigOperationResult,
      };
    } catch (error) {
      if (error instanceof AdminConfigServiceError) {
        if (error.payload.errorCode === "AUTH_REQUIRED") {
          throw redirect(302, resolveAuthRecoveryRedirectPath(error, returnTo));
        }
        return fail(error.payload.errorCode === "ACCESS_DENIED" ? 403 : 400, { error: error.payload });
      }
      return fail(500, {
        error: {
          title: "Ошибка сохранения",
          detail: "Не удалось сохранить конфиг-набор.",
          errorCode: "ADMIN_CONFIG_SAVE_FAILED",
        },
      });
    }
  },
  zod$(adminConfigSetFormSchema.extend({
    mode: z.enum(["create", "update"]),
    configSetId: z.string().min(1).optional(),
  }).refine((value) => value.mode === "create" || Boolean(value.configSetId), {
    message: "configSetId обязателен для update",
    path: ["configSetId"],
  })),
);

const configSetTargetSchema = z.object({ configSetId: z.string().min(1) });

export const useCompatibilityCheck = routeAction$(
  async (data, { sharedMap, cookie, fail, redirect, url }) => {
    const user = sharedMap.get("user") as SafeUserProfile;
    const requestContext = buildServerRequestContext({ sharedMap, cookie });
    const returnTo = `${url.pathname}${url.search}`;
    try {
      const result = await checkAdminConfigSetCompatibility(requestContext, {
        configSetId: data.configSetId,
        tenantId: user.tenant,
      });
      return {
        success: true as const,
        compatibility: result,
        operation: {
          kind: "compatibility",
          status: result.status ?? "UNKNOWN",
          message: result.status === "COMPATIBLE"
            ? "Compatibility check прошёл успешно."
            : "Compatibility check вернул несовместимости.",
          traceId: result.traceId,
          actorId: user.displayName,
        } satisfies AdminConfigOperationResult,
      };
    } catch (error) {
      if (error instanceof AdminConfigServiceError) {
        if (error.payload.errorCode === "AUTH_REQUIRED") {
          throw redirect(302, resolveAuthRecoveryRedirectPath(error, returnTo));
        }
        return fail(error.payload.errorCode === "ACCESS_DENIED" ? 403 : 400, { error: error.payload });
      }
      return fail(500, {
        error: {
          title: "Ошибка compatibility check",
          detail: "Не удалось выполнить compatibility check.",
          errorCode: "ADMIN_CONFIG_COMPATIBILITY_FAILED",
        },
      });
    }
  },
  zod$(configSetTargetSchema),
);

export const useRolloutConfigSet = routeAction$(
  async (data, { sharedMap, cookie, fail, redirect, url }) => {
    const user = sharedMap.get("user") as SafeUserProfile;
    const requestContext = await buildMutationRequestContext({ sharedMap, cookie });
    const returnTo = `${url.pathname}${url.search}`;
    try {
      const result = await rolloutAdminConfigSet(requestContext, {
        configSetId: data.configSetId,
        tenantId: user.tenant,
      });
      return {
        success: true as const,
        rollout: result,
        operation: {
          kind: "rollout",
          status: result.status ?? "UNKNOWN",
          message: `Статус развёртывания: ${result.status ?? "UNKNOWN"}`,
          traceId: null,
          actorId: result.actorId,
        } satisfies AdminConfigOperationResult,
      };
    } catch (error) {
      if (error instanceof AdminConfigServiceError) {
        if (error.payload.errorCode === "AUTH_REQUIRED") {
          throw redirect(302, resolveAuthRecoveryRedirectPath(error, returnTo));
        }
        return fail(error.payload.errorCode === "ACCESS_DENIED" ? 403 : 400, { error: error.payload });
      }
      return fail(500, {
        error: {
          title: "Ошибка развёртывания",
          detail: "Не удалось запустить развёртывание.",
          errorCode: "ADMIN_CONFIG_ROLLOUT_FAILED",
        },
      });
    }
  },
  zod$(configSetTargetSchema),
);

export const useRollbackConfigSet = routeAction$(
  async (data, { sharedMap, cookie, fail, redirect, url }) => {
    const user = sharedMap.get("user") as SafeUserProfile;
    const requestContext = await buildMutationRequestContext({ sharedMap, cookie });
    const returnTo = `${url.pathname}${url.search}`;
    try {
      const result = await rollbackAdminConfigSet(requestContext, {
        configSetId: data.configSetId,
        tenantId: user.tenant,
        environmentType: data.environmentType,
      });
      return {
        success: true as const,
        rollout: result,
        operation: {
          kind: "rollback",
          status: result.status ?? "UNKNOWN",
          message: `Статус отката: ${result.status ?? "UNKNOWN"}`,
          traceId: null,
          actorId: result.actorId,
        } satisfies AdminConfigOperationResult,
      };
    } catch (error) {
      if (error instanceof AdminConfigServiceError) {
        if (error.payload.errorCode === "AUTH_REQUIRED") {
          throw redirect(302, resolveAuthRecoveryRedirectPath(error, returnTo));
        }
        return fail(error.payload.errorCode === "ACCESS_DENIED" ? 403 : 400, { error: error.payload });
      }
      return fail(500, {
        error: {
          title: "Ошибка отката",
          detail: "Не удалось выполнить откат.",
          errorCode: "ADMIN_CONFIG_ROLLBACK_FAILED",
        },
      });
    }
  },
  zod$(configSetTargetSchema.extend({ environmentType: z.enum(["DEV", "TEST", "PROD"]) })),
);

function getActionError<T extends { error: ProblemDetail }>(value: unknown): ProblemDetail | null {
  if (!value || typeof value !== "object" || !("error" in value)) {
    return null;
  }
  return (value as T).error;
}

function getActionOperation<T extends { success: true; operation: AdminConfigOperationResult }>(value: unknown): AdminConfigOperationResult | null {
  if (!value || typeof value !== "object" || !("success" in value) || !("operation" in value)) {
    return null;
  }
  return (value as T).operation;
}

export default component$(() => {
  const loader = useAdminConfigSets();
  const saveAction = useSaveConfigSet();
  const compatibilityAction = useCompatibilityCheck();
  const rolloutAction = useRolloutConfigSet();
  const rollbackAction = useRollbackConfigSet();
  const location = useLocation();
  const { items, selectedConfig, capability, loadError, filters } = loader.value;

  if (loadError) {
    return (
      <ApiErrorAlert
        title={loadError.title ?? "Ошибка"}
        message={loadError.detail ?? "Неизвестная ошибка"}
        errorCode={loadError.errorCode ?? "ADMIN_CONFIG_UNKNOWN"}
        traceId={loadError.traceId}
      />
    );
  }

  const activeOperation =
    getActionOperation(saveAction.value) ??
    getActionOperation(compatibilityAction.value) ??
    getActionOperation(rolloutAction.value) ??
    getActionOperation(rollbackAction.value);

  const activeError =
    getActionError(saveAction.value) ??
    getActionError(compatibilityAction.value) ??
    getActionError(rolloutAction.value) ??
    getActionError(rollbackAction.value);

  const isCreateMode = filters.mode === "create";
  const rolloutBlocked = selectedConfig?.compatibility?.status === "INCOMPATIBLE";
  const triageReturnHref = resolveIncidentReturnTo(location.url, filters.environment);
  const overviewHref = buildOverviewHref(filters.environment);
  const visibleItems = selectedConfig
    ? items.map((item) => item.configSetId === selectedConfig.configSetId
      ? {
          ...item,
          latestRollout: selectedConfig.latestRollout ?? item.latestRollout,
          compatibilityStatus: selectedConfig.compatibility?.status ?? item.compatibilityStatus,
          compatibilityTraceId: selectedConfig.compatibility?.traceId ?? item.compatibilityTraceId,
        }
      : item)
    : items;

  return (
    <div class="space-y-6">
      <section class="rounded-3xl border border-border bg-surface px-6 py-5 shadow-sm">
        <nav aria-label="Контекст разбора" class="flex flex-wrap items-center gap-2 text-sm text-muted">
          <a href={overviewHref} class="underline">Сводка</a>
          <span>/</span>
          <a href={triageReturnHref} class="underline">Инциденты</a>
          <span>/</span>
          <span class="text-text">Вторичный модуль</span>
        </nav>
        <a href={triageReturnHref} class="mt-3 inline-block text-sm text-muted underline">Вернуться к очереди инцидентов</a>
        <div class="flex flex-col gap-4 lg:flex-row lg:items-end lg:justify-between">
          <div>
            <p class="mt-2 text-xs uppercase tracking-[0.22em] text-muted">Вторичный модуль</p>
            <h2 class="mt-2 text-xl font-semibold text-text">Конфиг-наборы</h2>
            <p class="mt-2 max-w-3xl text-sm text-muted">
              Единая зона для конфиг-наборов, проверки совместимости, развёртывания и отката без дублирования серверной логики.
            </p>
          </div>
          <form method="get" class="grid gap-3 sm:grid-cols-2 lg:min-w-[28rem]">
            {filters.returnTo ? <input type="hidden" name="returnTo" value={filters.returnTo} /> : null}
            <label class="flex flex-col gap-1 text-sm text-muted">
              Окружение
              <select class="rounded-2xl border border-border bg-bg px-3 py-2 text-text" name="environment" value={filters.environment}>
                {ENVIRONMENT_OPTIONS.map((environment) => (
                  <option key={environment || "all"} value={environment}>{environment || "все"}</option>
                ))}
              </select>
            </label>
            <label class="flex flex-col gap-1 text-sm text-muted">
              Статус
              <input class="rounded-2xl border border-border bg-bg px-3 py-2 text-text" type="text" name="status" value={filters.status} placeholder="active" />
            </label>
            <input type="hidden" name="configSetId" value={filters.configSetId} />
            <div class="sm:col-span-2 flex gap-2">
              <button type="submit" class="rounded-2xl border border-text bg-text px-4 py-2 text-sm font-medium text-bg">
                Применить фильтры
              </button>
              <a
                href={buildQueryHref(location.url, { environment: null, status: null, configSetId: null, mode: null })}
                class="rounded-2xl border border-border px-4 py-2 text-sm text-text"
              >
                Сбросить
              </a>
              <a
                href={buildQueryHref(location.url, { mode: "create", configSetId: null })}
                class="rounded-2xl border border-border px-4 py-2 text-sm text-text"
              >
                Новый набор
              </a>
            </div>
          </form>
        </div>
        <p class="mt-4 text-xs text-muted">
          Текущая роль оператора: {capability.role}. {capability.reason ?? "Роль admin может выполнять изменяющие операции с конфигурацией."}
        </p>
      </section>

      {activeError ? (
        <ApiErrorAlert
          title={activeError.title ?? "Ошибка"}
          message={activeError.detail ?? "Неизвестная ошибка"}
          errorCode={activeError.errorCode ?? "ADMIN_CONFIG_UNKNOWN"}
          traceId={activeError.traceId}
        />
      ) : null}

      {activeOperation ? (
        <section class="rounded-3xl border border-border bg-surface px-6 py-4 shadow-sm">
          <p class="text-xs uppercase tracking-[0.22em] text-muted">Последняя операция</p>
          <div class="mt-2 flex flex-col gap-1 text-sm text-text">
            <p>{activeOperation.message}</p>
            <p>Статус: {activeOperation.status}</p>
            <p>Оператор: {activeOperation.actorId ?? "n/a"}</p>
            <p>Trace ID: {activeOperation.traceId ?? "n/a"}</p>
          </div>
        </section>
      ) : null}

      <section class="grid gap-6 xl:grid-cols-[0.95fr_1.05fr]">
        <div class="space-y-4">
          <section class="rounded-3xl border border-border bg-surface p-5 shadow-sm">
            <div class="flex items-center justify-between gap-3">
              <h3 class="text-lg font-semibold text-text">Список конфиг-наборов</h3>
              <p class="text-sm text-muted">{visibleItems.length} элементов</p>
            </div>
            {visibleItems.length > 0 ? (
              <div class="mt-4 space-y-3">
                {visibleItems.map((item) => {
                  const active = item.configSetId === filters.configSetId && !isCreateMode;
                  return (
                    <a
                      key={item.configSetId}
                      href={buildQueryHref(location.url, { configSetId: item.configSetId, mode: null })}
                      class={[
                        "block rounded-2xl border px-4 py-4 transition-colors",
                        active ? "border-text bg-bg" : "border-border bg-bg hover:bg-surface-alt",
                      ]}
                    >
                      <div class="flex flex-col gap-2 sm:flex-row sm:items-start sm:justify-between">
                        <div>
                          <p class="text-sm font-medium text-text">{item.name}</p>
                          <p class="text-xs text-muted">{item.environmentType} · {item.status} · обновлён {item.updatedAt}</p>
                        </div>
                        <span class="text-xs uppercase tracking-[0.18em] text-muted">{item.latestRollout?.status ?? "NO_ROLLOUT"}</span>
                      </div>
                      <div class="mt-3 grid gap-1 text-xs text-muted">
                        <p>Issuer: {item.issuer}</p>
                        <p>URL сервиса встреч: {item.meetingsServiceUrl}</p>
                        <p>Последний оператор развёртывания: {item.latestRollout?.actorId ?? "не указан"}</p>
                        <p>Совместимость: {item.compatibilityStatus ?? "не проверялась"}</p>
                        {item.compatibilityTraceId ? <p>Trace ID проверки: {item.compatibilityTraceId}</p> : null}
                      </div>
                    </a>
                  );
                })}
              </div>
            ) : (
              <RequestStatePanel title="Конфиг-наборы не найдены" detail="Измените фильтры или создайте новый набор в пределах разрешённой роли." />
            )}
          </section>

          <section class="rounded-3xl border border-border bg-surface p-5 shadow-sm">
            <p class="text-xs uppercase tracking-[0.22em] text-muted">Дальнейшие шаги</p>
            <h3 class="mt-2 text-lg font-semibold text-text">Следующий этап раздела</h3>
            <p class="mt-2 text-sm text-muted">
              В текущем этапе приоритет отдан конфиг-наборам. Интерфейс управления ролями будет вынесен в отдельную поставку после подтверждения backend-контрактов, аудита и поддерживаемого сценария изменений.
            </p>
          </section>
        </div>

        <div class="space-y-4">
          <section class="rounded-3xl border border-border bg-surface p-5 shadow-sm">
            <div class="flex items-center justify-between gap-3">
              <h3 class="text-lg font-semibold text-text">
                {isCreateMode ? "Создание конфиг-набора" : "Карточка конфиг-набора"}
              </h3>
              {!isCreateMode && selectedConfig ? (
                <a
                  href={buildQueryHref(location.url, { mode: "edit", configSetId: selectedConfig.configSetId })}
                  class="text-sm font-medium underline"
                >
                  Редактировать
                </a>
              ) : null}
            </div>

            {isCreateMode || selectedConfig ? (
              <Form action={saveAction} class="mt-4 grid gap-3 sm:grid-cols-2">
                <input type="hidden" name="mode" value={isCreateMode ? "create" : "update"} />
                {!isCreateMode && selectedConfig ? <input type="hidden" name="configSetId" value={selectedConfig.configSetId} /> : null}

                <label class="flex flex-col gap-1 text-sm text-muted sm:col-span-2">
                  Название
                  <input class="rounded-2xl border border-border bg-bg px-3 py-2 text-text" name="name" value={selectedConfig?.name ?? ""} />
                </label>
                <label class="flex flex-col gap-1 text-sm text-muted">
                  Окружение
                  <select class="rounded-2xl border border-border bg-bg px-3 py-2 text-text" name="environmentType" value={normalizeAdminConfigEnvironment(selectedConfig?.environmentType ?? "DEV")}>
                    {["DEV", "TEST", "PROD"].map((environment) => (
                      <option key={environment} value={environment}>{environment}</option>
                    ))}
                  </select>
                </label>
                <label class="flex flex-col gap-1 text-sm text-muted">
                  Алгоритм
                  <input class="rounded-2xl border border-border bg-bg px-3 py-2 text-text" name="algorithm" value={selectedConfig?.algorithm ?? "HS256"} />
                </label>
                <label class="flex flex-col gap-1 text-sm text-muted">
                  Issuer
                  <input class="rounded-2xl border border-border bg-bg px-3 py-2 text-text" name="issuer" value={selectedConfig?.issuer ?? ""} />
                </label>
                <label class="flex flex-col gap-1 text-sm text-muted">
                  Audience
                  <input class="rounded-2xl border border-border bg-bg px-3 py-2 text-text" name="audience" value={selectedConfig?.audience ?? ""} />
                </label>
                <label class="flex flex-col gap-1 text-sm text-muted">
                  Claim роли
                  <input class="rounded-2xl border border-border bg-bg px-3 py-2 text-text" name="roleClaim" value={selectedConfig?.roleClaim ?? ""} />
                </label>
                <label class="flex flex-col gap-1 text-sm text-muted">
                  Секрет подписи
                  <input class="rounded-2xl border border-border bg-bg px-3 py-2 text-text" name="signingSecret" value="" placeholder={selectedConfig?.signingSecret ? "Оставьте пустым, чтобы не менять secret" : "secret"} />
                </label>
                <label class="flex flex-col gap-1 text-sm text-muted">
                  JWKS URI
                  <input class="rounded-2xl border border-border bg-bg px-3 py-2 text-text" name="jwksUri" value={selectedConfig?.jwksUri ?? ""} />
                </label>
                <label class="flex flex-col gap-1 text-sm text-muted">
                  Время жизни access token, мин.
                  <input class="rounded-2xl border border-border bg-bg px-3 py-2 text-text" type="number" name="accessTtlMinutes" value={String(selectedConfig?.accessTtlMinutes ?? 15)} />
                </label>
                <label class="flex flex-col gap-1 text-sm text-muted">
                  Время жизни refresh token, мин.
                  <input class="rounded-2xl border border-border bg-bg px-3 py-2 text-text" type="number" name="refreshTtlMinutes" value={String(selectedConfig?.refreshTtlMinutes ?? 60)} />
                </label>
                <label class="flex flex-col gap-1 text-sm text-muted sm:col-span-2">
                  URL сервиса встреч
                  <input class="rounded-2xl border border-border bg-bg px-3 py-2 text-text" name="meetingsServiceUrl" value={selectedConfig?.meetingsServiceUrl ?? "https://"} />
                </label>
                <div class="sm:col-span-2 flex flex-wrap gap-2">
                  <button
                    type="submit"
                    disabled={!capability.canMutate || saveAction.isRunning}
                    class="rounded-2xl border border-text bg-text px-4 py-2 text-sm font-medium text-bg disabled:cursor-not-allowed disabled:opacity-50"
                  >
                    {saveAction.isRunning ? "Сохранение..." : isCreateMode ? "Создать" : "Сохранить изменения"}
                  </button>
                  {!capability.canMutate ? <span class="text-xs text-muted">Изменяющие операции недоступны для роли {capability.role}.</span> : null}
                </div>
              </Form>
            ) : (
              <RequestStatePanel title="Выберите конфиг-набор" detail="Откройте карточку из списка слева или создайте новый набор." />
            )}
          </section>

          {selectedConfig ? (
            <section class="rounded-3xl border border-border bg-surface p-5 shadow-sm">
              <div class="flex items-center justify-between gap-3">
                <h3 class="text-lg font-semibold text-text">Операции</h3>
                <p class="text-sm text-muted">ProblemDetail и trace ID возвращаются сервером без обходных клиентских решений.</p>
              </div>
              <div class="mt-4 grid gap-4 lg:grid-cols-[1fr_1fr]">
                <div class="rounded-2xl border border-border bg-bg p-4">
                  <p class="text-sm font-medium text-text">Совместимость</p>
                  <p class="mt-2 text-xs text-muted">Статус: {selectedConfig.compatibility?.status ?? "не проверялась"}</p>
                  <p class="text-xs text-muted">Trace ID: {selectedConfig.compatibility?.traceId ?? "n/a"}</p>
                  {selectedConfig.compatibility?.mismatches.length ? (
                    <ul class="mt-3 space-y-1 text-xs text-muted">
                      {selectedConfig.compatibility.mismatches.map((mismatch, index) => (
                        <li key={`${mismatch.code ?? "mismatch"}-${index}`}>
                          {mismatch.code ?? "MISMATCH"}: {mismatch.message ?? "Без описания"}
                        </li>
                      ))}
                    </ul>
                  ) : null}
                  <Form action={compatibilityAction} class="mt-4">
                    <input type="hidden" name="configSetId" value={selectedConfig.configSetId} />
                    <button type="submit" class="rounded-2xl border border-border px-4 py-2 text-sm text-text">
                      {compatibilityAction.isRunning ? "Проверяем..." : "Проверить совместимость"}
                    </button>
                  </Form>
                </div>

                <div class="rounded-2xl border border-border bg-bg p-4">
                  <p class="text-sm font-medium text-text">Развёртывание и откат</p>
                  <p class="mt-2 text-xs text-muted">Последний статус: {selectedConfig.latestRollout?.status ?? "NO_ROLLOUT"}</p>
                  <p class="text-xs text-muted">Оператор: {selectedConfig.latestRollout?.actorId ?? "не указан"}</p>
                  <p class="text-xs text-muted">Ошибки валидации: {selectedConfig.latestRollout?.validationErrors ?? "нет"}</p>
                  <div class="mt-4 flex flex-wrap gap-2">
                    <Form action={rolloutAction}>
                      <input type="hidden" name="configSetId" value={selectedConfig.configSetId} />
                      <button
                        type="submit"
                        disabled={!capability.canMutate || rolloutBlocked || rolloutAction.isRunning}
                        class="rounded-2xl border border-text bg-text px-4 py-2 text-sm font-medium text-bg disabled:cursor-not-allowed disabled:opacity-50"
                      >
                        {rolloutAction.isRunning ? "Запуск развёртывания..." : "Запустить развёртывание"}
                      </button>
                    </Form>
                    <Form action={rollbackAction}>
                      <input type="hidden" name="configSetId" value={selectedConfig.configSetId} />
                      <input type="hidden" name="environmentType" value={normalizeAdminConfigEnvironment(selectedConfig.environmentType)} />
                      <button
                        type="submit"
                        disabled={!capability.canMutate || rollbackAction.isRunning}
                        class="rounded-2xl border border-border px-4 py-2 text-sm text-text disabled:cursor-not-allowed disabled:opacity-50"
                      >
                        {rollbackAction.isRunning ? "Откат..." : "Выполнить откат"}
                      </button>
                    </Form>
                  </div>
                  {rolloutBlocked ? (
                    <p class="mt-3 text-xs text-muted">Развёртывание заблокировано: проверка совместимости завершилась со статусом INCOMPATIBLE.</p>
                  ) : null}
                </div>
              </div>
            </section>
          ) : null}
        </div>
      </section>
    </div>
  );
});

export const head: DocumentHead = {
  title: "Конфиг-наборы администратора — Jitsi Portal",
  meta: [
    {
      name: "description",
      content: "Управление конфиг-наборами, проверкой совместимости, развёртыванием и откатом из административной консоли.",
    },
  ],
};