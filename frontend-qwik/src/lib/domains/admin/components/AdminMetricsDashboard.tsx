import { $, component$, useSignal, useStore, useVisibleTask$, type QRL } from "@qwik.dev/core";
import { startVisiblePolling } from "../../../shared/api";
import { fetchMetricDashboard, fetchMetrics } from "../admin-metrics.service";
import { metricPeriodSchema, type Dashboard, type MetricDescriptor, type MetricsSnapshot } from "../admin-metrics.types";
import { formatMetric, MetricChart } from "./MetricChart";

export const AdminMetricsDashboard = component$<{
  catalog: MetricDescriptor[]; dashboard: Dashboard; snapshot: MetricsSnapshot;
  onSave$: QRL<(input: Dashboard) => Promise<{ dashboard?: Dashboard; status: number }>>;
}>(({ catalog, dashboard, snapshot, onSave$ }) => {
  const layout = useStore<Dashboard>({ ...dashboard, widgets: [...dashboard.widgets] });
  const data = useSignal<MetricsSnapshot | null>(snapshot);
  const busy = useSignal(false), saving = useSignal(false), dirty = useSignal(false), conflict = useSignal(false), stopped = useSignal(false), failed = useSignal(false);
  const message = useSignal(""), search = useSignal(""), selected = useSignal("");
  const refresh = $(async (signal?: AbortSignal) => {
    if (stopped.value) return false;
    if (busy.value) return true;
    busy.value = true;
    try {
      const ids = layout.widgets.map(w => w.metricId), period = layout.period;
      const next = await fetchMetrics(import.meta.env.VITE_API_URL || "/api/v1", ids, period, signal);
      if (layout.period === period && layout.widgets.map(w => w.metricId).join(",") === ids.join(",")) data.value = next;
      failed.value = false;
    } catch (error) {
      failed.value = true;
      if (typeof error === "object" && error !== null && "status" in error && (error.status === 401 || error.status === 403)) {
        data.value = null; stopped.value = true;
        message.value = "Доступ завершён. Выполните вход снова.";
      }
    } finally { busy.value = false; }
    return !stopped.value;
  });
  // eslint-disable-next-line qwik/no-use-visible-task
  useVisibleTask$(({ cleanup }) => { cleanup(startVisiblePolling(signal => refresh(signal))); });
  const change = $(() => { dirty.value = true; data.value = null; });
  const add = $(() => {
    const descriptor = catalog.find(d => d.id === selected.value);
    if (descriptor && layout.widgets.length < 12 && !layout.widgets.some(w => w.metricId === descriptor.id)) {
      layout.widgets = [...layout.widgets, { metricId: descriptor.id, view: descriptor.views[0] }];
      dirty.value = true; selected.value = "";
    }
  });
  const reorder = $((index: number, direction: number) => {
    const next = index + direction;
    if (next < 0 || next >= layout.widgets.length) return;
    const widgets = [...layout.widgets];
    [widgets[index], widgets[next]] = [widgets[next], widgets[index]];
    layout.widgets = widgets; dirty.value = true;
  });
  const save = $(async () => {
    if (saving.value || conflict.value || stopped.value) return;
    saving.value = true;
    try {
      const result = await onSave$({ revision: layout.revision, period: layout.period, widgets: [...layout.widgets] });
      if (result.dashboard) {
        layout.revision = result.dashboard.revision; dirty.value = false; message.value = "Настройки сохранены.";
      } else {
        conflict.value = result.status === 409;
        message.value = conflict.value ? "Настройки изменились в другой вкладке. Ваши изменения сохранены на этой странице; загрузите актуальные настройки перед повторным сохранением." : "Сохранение не выполнено. Повторите попытку.";
      }
    } finally { saving.value = false; }
  });
  const reload = $(async () => {
    try {
      const saved = await fetchMetricDashboard(import.meta.env.VITE_API_URL || "/api/v1");
      layout.revision = saved.revision; layout.period = saved.period; layout.widgets = saved.widgets;
      dirty.value = false; conflict.value = false; message.value = "Сохранённые настройки загружены.";
      await refresh();
    } catch { message.value = "Не удалось загрузить настройки."; }
  });
  const available = catalog.filter(d => !layout.widgets.some(w => w.metricId === d.id) && `${d.title} ${d.description}`.toLocaleLowerCase("ru-RU").includes(search.value.toLocaleLowerCase("ru-RU")));
  const labels = { ok: "Актуальные данные", no_data: "Нет данных", no_traffic: "Нет запросов", stale: "Данные устарели", unavailable: "Источник недоступен", partial: "Неполные данные" };
  return <section class="space-y-4" aria-label="Дашборд метрик">
    <div><h1 class="text-2xl font-semibold">Метрики сервера</h1><p class="mt-1 text-sm text-muted">Системные показатели текущего развёртывания. Выдача JWT не подтверждает качество видеосвязи.</p></div>
    <div class="flex flex-wrap items-end gap-3 rounded-xl border border-border bg-surface p-4">
      <label class="text-sm">Период<select disabled={saving.value || stopped.value} class="mt-1 block rounded border border-border bg-bg p-2" value={layout.period} onChange$={(_, el) => { layout.period = metricPeriodSchema.parse(el.value); void change(); }}>
        {[["15m", "15 минут"], ["1h", "1 час"], ["6h", "6 часов"], ["24h", "24 часа"], ["7d", "7 дней"]].map(([value, label]) => <option key={value} value={value}>{label}</option>)}
      </select></label>
      <button type="button" class="rounded border border-border px-3 py-2 disabled:opacity-50" disabled={busy.value || stopped.value} onClick$={() => refresh()}>Обновить показатели</button>
      <button type="button" class="rounded bg-primary px-3 py-2 text-white disabled:opacity-50" disabled={!dirty.value || saving.value || conflict.value || stopped.value} onClick$={save}>Сохранить дашборд</button>
      {conflict.value && <button type="button" class="rounded border border-border px-3 py-2" onClick$={reload}>Загрузить сохранённые настройки</button>}
      <p class="text-sm text-muted" role="status">{message.value || (dirty.value ? "Есть несохранённые изменения" : "Личные настройки")}</p>
    </div>
    <fieldset class="flex flex-wrap items-end gap-3 rounded-xl border border-border p-4" disabled={saving.value || stopped.value}>
      <legend class="px-1 font-medium">Добавить показатель ({layout.widgets.length}/12)</legend>
      <label class="text-sm">Поиск<input type="search" class="mt-1 block w-full rounded border border-border bg-bg p-2" value={search.value} onInput$={(_, el) => { search.value = el.value; }} /></label>
      <label class="min-w-0 flex-1 text-sm">Каталог<select class="mt-1 block w-full rounded border border-border bg-bg p-2" value={selected.value} onChange$={(_, el) => { selected.value = el.value; }}><option value="">Выберите показатель</option>{available.map(d => <option key={d.id} value={d.id}>{d.title}</option>)}</select></label>
      <button type="button" class="rounded border border-border px-3 py-2 disabled:opacity-50" disabled={!selected.value || layout.widgets.length >= 12} onClick$={add}>Добавить</button>
    </fieldset>
    {failed.value && <p role="status" class="text-sm text-muted">Обновление недоступно. Предыдущие показания могут быть устаревшими.</p>}
    {layout.widgets.length === 0 && <p class="rounded-xl border border-border p-6 text-muted">Дашборд пуст. Добавьте показатели из каталога.</p>}
    <div class="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-3">{layout.widgets.map((widget, index) => {
      const descriptor = catalog.find(d => d.id === widget.metricId);
      if (!descriptor) return null;
      const reading = data.value?.metrics.find(m => m.id === widget.metricId);
      const stale = failed.value || (!!reading?.measuredAt && Date.now() - Date.parse(reading.measuredAt) > 90_000);
      const value = !stale && (reading?.state === "ok" || reading?.state === "partial") ? reading.value : null;
      return <article key={widget.metricId} class="min-w-0 rounded-xl border border-border bg-surface p-4">
        <h2 class="font-semibold">{descriptor.title}</h2><p class="mt-1 text-xs text-muted">{descriptor.description}</p>
        <p class="mt-3 text-2xl font-semibold">{formatMetric(value, descriptor.unit)}</p>
        <p class="mt-1 text-sm text-muted">{stale ? labels.stale : labels[reading?.state ?? "no_data"]}{descriptor.id === "host.disk" && value != null && value >= 90 ? value >= 95 ? " · Критически мало места" : " · Мало свободного места" : ""}</p>
        {widget.view === "line" && <MetricChart title={descriptor.title} unit={descriptor.unit} series={reading?.series ?? []} />}
        {reading?.measuredAt && <p class="mt-2 text-xs text-muted">Измерено: <time dateTime={reading.measuredAt}>{new Date(reading.measuredAt).toLocaleString("ru-RU")}</time></p>}
        <div class="mt-4 flex flex-wrap gap-2">
          <select disabled={saving.value || stopped.value} aria-label={`Вид: ${descriptor.title}`} class="rounded border border-border bg-bg p-1 text-sm" value={widget.view} onChange$={(_, el) => { layout.widgets[index].view = el.value === "line" ? "line" : "card"; dirty.value = true; }}>{descriptor.views.map(view => <option key={view} value={view}>{view === "card" ? "Карточка" : "График"}</option>)}</select>
          <button type="button" class="rounded border border-border px-2 disabled:opacity-50" aria-label={`Поднять: ${descriptor.title}`} disabled={index === 0 || saving.value} onClick$={() => reorder(index, -1)}>↑</button>
          <button type="button" class="rounded border border-border px-2 disabled:opacity-50" aria-label={`Опустить: ${descriptor.title}`} disabled={index === layout.widgets.length - 1 || saving.value} onClick$={() => reorder(index, 1)}>↓</button>
          <button type="button" class="rounded border border-border px-2 text-sm" disabled={saving.value} onClick$={() => { layout.widgets = layout.widgets.filter((_, i) => i !== index); dirty.value = true; }}>Удалить</button>
        </div>
      </article>;
    })}</div>
  </section>;
});
