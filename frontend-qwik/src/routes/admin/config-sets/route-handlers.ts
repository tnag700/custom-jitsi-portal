import { routeAction$, routeLoader$, z, zod$ } from "@qwik.dev/router";
import {
  resolveAuthRecoveryRedirectPath,
  type SafeUserProfile,
} from "~/lib/domains/auth";
import {
  AdminConfigServiceError,
  adminConfigSetFormSchema,
  buildAdminConfigRouteFilters,
  checkAdminConfigSetCompatibility,
  createAdminConfigSet,
  fetchAdminConfigSet,
  fetchAdminConfigSets,
  filterAdminConfigSummaries,
  loadAdminConfigLatestRollouts,
  normalizeAdminConfigEnvironment,
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
import type { ProblemDetail } from "~/lib/shared";
import {
  buildMutationRequestContext,
  buildServerRequestContext,
} from "~/lib/shared/routes/server-handlers";

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

// eslint-disable-next-line qwik/loader-location
export const useAdminConfigSets = routeLoader$(
  async ({ sharedMap, cookie, query, redirect, url }) => {
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
      const rolloutByEnvironment = await loadAdminConfigLatestRollouts(
        requestContext,
        user.tenant,
        page.items,
      );
      const items = filterAdminConfigSummaries(
        page.items.map((item) => ({
          ...item,
          latestRollout:
            rolloutByEnvironment.get(
              normalizeAdminConfigEnvironment(item.environmentType),
            ) ?? null,
          capability,
        })),
        filters,
      );

      const selectedConfigId = resolveAdminConfigSelectedId(filters, items);
      const selectedConfig = shouldLoadAdminConfigDetail(
        filters,
        selectedConfigId,
      )
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
  },
);

// eslint-disable-next-line qwik/loader-location
export const useSaveConfigSet = routeAction$(
  async (data, { sharedMap, cookie, fail, redirect, url }) => {
    const user = sharedMap.get("user") as SafeUserProfile;
    const requestContext = await buildMutationRequestContext({
      sharedMap,
      cookie,
    });
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
      const saved =
        data.mode === "create"
          ? await createAdminConfigSet(requestContext, payload)
          : await updateAdminConfigSet(
              requestContext,
              configSetId as string,
              payload,
            );
      return {
        success: true as const,
        configSet: saved,
        operation: {
          kind: "save",
          status: saved.status,
          message:
            data.mode === "create"
              ? "Конфиг-набор создан."
              : "Конфиг-набор обновлён.",
          traceId: null,
          actorId: user.displayName,
        } satisfies AdminConfigOperationResult,
      };
    } catch (error) {
      if (error instanceof AdminConfigServiceError) {
        if (error.payload.errorCode === "AUTH_REQUIRED") {
          throw redirect(302, resolveAuthRecoveryRedirectPath(error, returnTo));
        }
        return fail(error.payload.errorCode === "ACCESS_DENIED" ? 403 : 400, {
          error: error.payload,
        });
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
  zod$(
    adminConfigSetFormSchema
      .extend({
        mode: z.enum(["create", "update"]),
        configSetId: z.string().min(1).optional(),
      })
      .refine(
        (value) => value.mode === "create" || Boolean(value.configSetId),
        {
          message: "configSetId обязателен для update",
          path: ["configSetId"],
        },
      ),
  ),
);

const configSetTargetSchema = z.object({ configSetId: z.string().min(1) });

// eslint-disable-next-line qwik/loader-location
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
          message:
            result.status === "COMPATIBLE"
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
        return fail(error.payload.errorCode === "ACCESS_DENIED" ? 403 : 400, {
          error: error.payload,
        });
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

// eslint-disable-next-line qwik/loader-location
export const useRolloutConfigSet = routeAction$(
  async (data, { sharedMap, cookie, fail, redirect, url }) => {
    const user = sharedMap.get("user") as SafeUserProfile;
    const requestContext = await buildMutationRequestContext({
      sharedMap,
      cookie,
    });
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
        return fail(error.payload.errorCode === "ACCESS_DENIED" ? 403 : 400, {
          error: error.payload,
        });
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

// eslint-disable-next-line qwik/loader-location
export const useRollbackConfigSet = routeAction$(
  async (data, { sharedMap, cookie, fail, redirect, url }) => {
    const user = sharedMap.get("user") as SafeUserProfile;
    const requestContext = await buildMutationRequestContext({
      sharedMap,
      cookie,
    });
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
        return fail(error.payload.errorCode === "ACCESS_DENIED" ? 403 : 400, {
          error: error.payload,
        });
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
  zod$(
    configSetTargetSchema.extend({
      environmentType: z.enum(["DEV", "TEST", "PROD"]),
    }),
  ),
);

export function getAdminConfigActionError(
  value: unknown,
): ProblemDetail | null {
  if (!value || typeof value !== "object" || !("error" in value)) {
    return null;
  }
  return (value as { error: ProblemDetail }).error;
}

export function getAdminConfigActionOperation(
  value: unknown,
): AdminConfigOperationResult | null {
  if (
    !value ||
    typeof value !== "object" ||
    !("success" in value) ||
    !("operation" in value)
  ) {
    return null;
  }
  return (value as { operation: AdminConfigOperationResult }).operation;
}
