import { component$ } from "@qwik.dev/core";
import type { MetricPoint, MetricSeries } from "../admin-metrics.types";

export function formatMetric(value: number | null | undefined, unit: string): string {
  if (value == null || !Number.isFinite(value)) return "Нет данных";
  if (unit === "boolean") return value === 1 ? "Да" : "Нет";
  if (unit === "bytes") return `${(value / 1_048_576).toLocaleString("ru-RU", { maximumFractionDigits: 1 })} МиБ`;
  return `${value.toLocaleString("ru-RU", { maximumFractionDigits: 1 })}${unit === "percent" ? "%" : unit === "milliseconds" ? " мс" : " / мин"}`;
}

export function chartGeometry(samples: MetricPoint[]) {
  const valid = samples.filter(p => p.value != null && Number.isFinite(p.value) && Number.isFinite(Date.parse(p.time)));
  if (!valid.length) return { paths: [] as string[], points: [] as { x: number; y: number; sample: MetricPoint }[] };
  const times = samples.map(p => Date.parse(p.time)).filter(Number.isFinite);
  const start = Math.min(...times), duration = Math.max(1, Math.max(...times) - start);
  const minimum = Math.min(0, ...valid.map(p => p.value!));
  const range = Math.max(1, Math.max(...valid.map(p => p.value!)) - minimum);
  const paths: string[] = [], points: { x: number; y: number; sample: MetricPoint }[] = [];
  let path = "";
  for (const sample of samples) {
    if (sample.value == null || !Number.isFinite(sample.value) || !Number.isFinite(Date.parse(sample.time))) {
      if (path) paths.push(path);
      path = "";
      continue;
    }
    const x = (Date.parse(sample.time) - start) / duration * 100;
    const y = 95 - (sample.value - minimum) / range * 90;
    path += `${path ? " L" : "M"}${x.toFixed(3)},${y.toFixed(3)}`;
    points.push({ x, y, sample });
  }
  if (path) paths.push(path);
  return { paths, points };
}

export const MetricChart = component$<{ series: MetricSeries[]; unit: string; title: string }>(({ series, unit, title }) => {
  const geometry = series.map(s => ({ name: s.name, ...chartGeometry(s.points) }));
  const available = geometry.some(s => s.points.length > 0);
  return <figure>
    {available ? <svg role="img" aria-label={`${title}: история значений. Разрывы означают отсутствие данных.`} viewBox="0 0 100 100" preserveAspectRatio="none" class="mt-3 h-36 w-full text-primary">
      <title>{title}</title>
      {geometry.map((s, i) => <g key={i}>{s.paths.map((path, j) => <path key={j} d={path} fill="none" stroke="currentColor" stroke-width="1.5" vector-effect="non-scaling-stroke" />)}
        {s.points.map((point, j) => <circle key={j} cx={point.x} cy={point.y} r="0.7" fill="currentColor"><title>{`${new Date(point.sample.time).toLocaleString("ru-RU")}: ${formatMetric(point.sample.value, unit)}`}</title></circle>)}
      </g>)}
    </svg> : <p class="py-8 text-sm text-muted">Нет данных за выбранный период</p>}
    {available && <figcaption class="mt-2 text-xs text-muted">{series[0]?.points[0]?.time && new Date(series[0].points[0].time).toLocaleString("ru-RU")} — {series[0]?.points.at(-1)?.time && new Date(series[0].points.at(-1)!.time).toLocaleString("ru-RU")}</figcaption>}
  </figure>;
});
