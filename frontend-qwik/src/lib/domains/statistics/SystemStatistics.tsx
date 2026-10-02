import { $, component$, useSignal, useVisibleTask$ } from "@qwik.dev/core";
import { startVisiblePolling } from "../../shared/api";
import { fetchSystemStatistics } from "./statistics.service";
import type { Summary } from "./types";

export const SystemStatistics = component$<{ initial: Summary | null; isAdmin: boolean }>(({ initial, isAdmin }) => {
  const data = useSignal(initial);
  const busy = useSignal(false);
  const stopped = useSignal(false);
  const failed = useSignal(false);
  const refresh = $(async (signal?: AbortSignal) => {
    if (stopped.value) return false;
    if (busy.value) return true;
    busy.value = true;
    try {
      data.value = await fetchSystemStatistics(import.meta.env.VITE_API_URL || "/api/v1", signal);
      failed.value = false;
    } catch (error) {
      failed.value = true;
      if (typeof error === "object" && error !== null && "status" in error && error.status === 401) {
        data.value = null;
        stopped.value = true;
      }
    } finally { busy.value = false; }
    return !stopped.value;
  });
  // eslint-disable-next-line qwik/no-use-visible-task
  useVisibleTask$(({ cleanup }) => {
    cleanup(startVisiblePolling((signal) => refresh(signal)));
  });
  const stale = failed.value || data.value?.stale || (!!data.value?.measuredAt && Date.now() - Date.parse(data.value.measuredAt) > 90_000);
  const percent = (value: number | null | undefined) => value == null || stale ? "Нет данных" : `≈ ${value}%`;
  return <section aria-label="Статистика сервера" class="mb-5 rounded-xl border border-border bg-surface p-4">
    <div class="mb-3 flex flex-wrap items-center justify-between gap-2">
      <h2 class="font-semibold">Статистика сервера</h2>
      <div class="flex items-center gap-3">
        {isAdmin && <a class="text-primary underline" href="/admin/metrics">Подробные метрики</a>}
        <button type="button" class="rounded border border-border px-3 py-1 disabled:opacity-50" disabled={busy.value || stopped.value} onClick$={() => refresh()}>Обновить</button>
      </div>
    </div>
    <dl class="grid grid-cols-2 gap-3 sm:grid-cols-4">
      <div><dt class="text-sm text-muted">Backend</dt><dd>{stale ? "Нет данных" : data.value?.backendState === "working" ? "Работает" : data.value?.backendState === "problem" ? "Есть проблема" : "Нет данных"}</dd></div>
      <div><dt class="text-sm text-muted">Процессор</dt><dd>{percent(data.value?.cpuPercent)}</dd></div>
      <div><dt class="text-sm text-muted">Память</dt><dd>{percent(data.value?.memoryPercent)}</dd></div>
      <div><dt class="text-sm text-muted">Диск</dt><dd>{stale ? "Нет данных" : data.value?.diskState === "sufficient" ? "Места достаточно" : data.value?.diskState === "low" ? "Мало свободного места" : "Нет данных"}</dd></div>
    </dl>
    <p class="mt-3 text-xs text-muted" aria-live="polite">{stopped.value ? "Сессия завершена. Войдите снова." : stale ? "Данные устарели или обновление недоступно." : data.value?.monitoringConfigured === false ? "Мониторинг пока не подключён." : data.value?.measuredAt ? <>Измерено: <time dateTime={data.value.measuredAt}>{new Date(data.value.measuredAt).toLocaleString("ru-RU")}</time></> : "Нет данных мониторинга."}</p>
  </section>;
});
