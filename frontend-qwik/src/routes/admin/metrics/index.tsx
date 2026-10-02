import { component$ } from "@qwik.dev/core";
import { routeAction$, routeLoader$, type DocumentHead } from "@qwik.dev/router";
import { AdminMetricsDashboard, fetchMetricsCatalog, fetchMetricDashboard, fetchMetrics, saveMetricDashboard } from "~/lib/domains/admin";
import { resolveAuthRecoveryRedirectPath, type SafeUserProfile } from "~/lib/domains/auth";
import { buildMutationRequestContext, buildServerRequestContext } from "~/lib/shared/routes/server-handlers";
import { hasPlatformAdminAccess } from "~/lib/shared/security/access-claims";

function status(error: unknown) {
  return typeof error === "object" && error !== null && "status" in error && typeof error.status === "number" ? error.status : 500;
}

export const useMetricsDashboard = routeLoader$(async ({ sharedMap, cookie, url, redirect, cacheControl }) => {
  cacheControl?.({ private: true, noStore: true });
  const user = sharedMap.get("user") as SafeUserProfile | null;
  if (!user || !hasPlatformAdminAccess(user.claims)) throw redirect(302, "/");
  try {
    const context = buildServerRequestContext({ sharedMap, cookie });
    const [catalog, dashboard] = await Promise.all([fetchMetricsCatalog(context), fetchMetricDashboard(context)]);
    const snapshot = dashboard.widgets.length === 0 ? { generatedAt: new Date().toISOString(), metrics: [] }
      : await fetchMetrics(context, dashboard.widgets.map(w => w.metricId), dashboard.period);
    return { catalog, dashboard, snapshot, error: null };
  } catch (error) {
    if (status(error) === 401) throw redirect(302, resolveAuthRecoveryRedirectPath(undefined, `${url.pathname}${url.search}`));
    if (status(error) === 403) throw redirect(302, "/");
    return { catalog: [], dashboard: null, snapshot: null, error: "Не удалось загрузить метрики и настройки. Обновите страницу." };
  }
});

export const useSaveMetricDashboard = routeAction$(async (input, { sharedMap, cookie, fail, redirect, url }) => {
  const user = sharedMap.get("user") as SafeUserProfile | null;
  if (!user || !hasPlatformAdminAccess(user.claims)) return fail(403, { dashboard: undefined });
  try { return { dashboard: await saveMetricDashboard(await buildMutationRequestContext({ sharedMap, cookie }), input) }; }
  catch (error) {
    if (status(error) === 401 || status(error) === 403) throw redirect(302, resolveAuthRecoveryRedirectPath(undefined, `${url.pathname}${url.search}`));
    return fail(status(error), { dashboard: undefined });
  }
});

export default component$(() => {
  const loaded = useMetricsDashboard(), save = useSaveMetricDashboard();
  const { catalog, dashboard, snapshot, error } = loaded.value;
  if (!dashboard || !snapshot) return <p role="alert" class="rounded border border-border p-4">{error}</p>;
  return <AdminMetricsDashboard catalog={catalog} dashboard={dashboard} snapshot={snapshot} onSave$={async input => {
    const result = await save.submit(input);
    return { dashboard: result.value.dashboard, status: result.status ?? 200 };
  }} />;
});

export const head: DocumentHead = { title: "Метрики сервера — Jitsi Portal" };
