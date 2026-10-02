import { fetchWithTimeout } from "../../shared/api";
import type { ServerRequestContext, MutationRequestContext } from "../../shared/routes/server-handlers";
import { dashboardSchema, metricDescriptorSchema, snapshotSchema } from "./admin-metrics.types";

async function request(context: ServerRequestContext | string, path: string, init?: RequestInit) {
  const { apiUrl, headers } = typeof context === "string" ? { apiUrl: context, headers: {} } : context;
  const response = await fetchWithTimeout(`${apiUrl}/admin/metrics/${path}`, {
    credentials: "include", cache: "no-store", ...init, headers: { ...headers, ...init?.headers },
  });
  if (!response.ok) throw Object.assign(new Error("Метрики недоступны"), { status: response.status });
  return response.json();
}

export async function fetchMetricsCatalog(context: ServerRequestContext) {
  return metricDescriptorSchema.array().parse(await request(context, "catalog"));
}
export async function fetchMetricDashboard(context: ServerRequestContext | string) {
  return dashboardSchema.parse(await request(context, "dashboard"));
}
export async function fetchMetrics(context: ServerRequestContext | string, ids: string[], period: string, signal?: AbortSignal) {
  if (ids.length === 0) return { generatedAt: new Date().toISOString(), metrics: [] };
  const params = new URLSearchParams({ ids: ids.join(","), period });
  return snapshotSchema.parse(await request(context, `query?${params}`, { signal }));
}
export async function saveMetricDashboard(context: MutationRequestContext, input: unknown) {
  return dashboardSchema.parse(await request(context, "dashboard", {
    method: "PUT", headers: { "Content-Type": "application/json" }, body: JSON.stringify(dashboardSchema.parse(input)),
  }));
}
