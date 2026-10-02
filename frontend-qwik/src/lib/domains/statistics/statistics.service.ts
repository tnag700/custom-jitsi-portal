import { fetchWithTimeout } from "../../shared/api";
import type { ServerRequestContext } from "../../shared/routes/server-handlers";
import { summarySchema, type Summary } from "./types";

export async function fetchSystemStatistics(context: ServerRequestContext | string, signal?: AbortSignal): Promise<Summary> {
  const { apiUrl, headers } = typeof context === "string" ? { apiUrl: context, headers: {} } : context;
  const response = await fetchWithTimeout(`${apiUrl}/system/statistics`, {
    headers, credentials: "include", cache: "no-store", signal,
  });
  if (!response.ok) throw Object.assign(new Error("Статистика недоступна"), { status: response.status });
  return summarySchema.parse(await response.json());
}
