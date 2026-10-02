import { component$, useSignal, useVisibleTask$ } from "@qwik.dev/core";
import type { MetricPoint, MetricSeries } from "../admin-metrics.types";

export function formatMetric(value: number | null | undefined, unit: string): string {
  if (value == null || !Number.isFinite(value)) return "Нет данных";
  if (unit === "boolean") return value === 1 ? "Да" : "Нет";
  if (unit === "bytes") return `${(value / 1_048_576).toLocaleString("ru-RU", { maximumFractionDigits: 1 })} МиБ`;
  return `${value.toLocaleString("ru-RU", { maximumFractionDigits: 1 })}${unit === "percent" ? "%" : unit === "milliseconds" ? " мс" : " / мин"}`;
}

type ChartDomain = { start: number; end: number; minimum: number; maximum: number };

export function chartGeometry(samples: MetricPoint[], domain?: ChartDomain) {
  const valid = samples.filter(p => p.value != null && Number.isFinite(p.value) && Number.isFinite(Date.parse(p.time)));
  const times = samples.map(p => Date.parse(p.time)).filter(Number.isFinite);
  const start = domain?.start ?? (times.length ? Math.min(...times) : 0);
  const end = domain?.end ?? (times.length ? Math.max(...times) : start);
  const duration = Math.max(1, end - start);
  const minimum = domain?.minimum ?? Math.min(0, ...valid.map(p => p.value!));
  const range = domain ? domain.maximum - minimum : Math.max(1, Math.max(0, ...valid.map(p => p.value!)) - minimum);
  const paths: string[] = [], points: { x: number; y: number; sample: MetricPoint }[] = [];
  const gaps: { start: number; end: number }[] = [];
  let path = "";
  let gapStart: number | undefined;
  let previousTime = times[0] ?? start;
  for (const sample of samples) {
    const time = Date.parse(sample.time);
    if (sample.value == null || !Number.isFinite(sample.value) || !Number.isFinite(Date.parse(sample.time))) {
      if (path) paths.push(path);
      path = "";
      if (gapStart == null) gapStart = previousTime;
      continue;
    }
    if (gapStart != null) {
      gaps.push({ start: (gapStart - start) / duration * 100, end: (time - start) / duration * 100 });
      gapStart = undefined;
    }
    const x = (time - start) / duration * 100;
    const y = 100 - (sample.value - minimum) / range * 100;
    path += `${path ? " L" : "M"}${x.toFixed(3)},${y.toFixed(3)}`;
    points.push({ x, y, sample });
    previousTime = time;
  }
  if (path) paths.push(path);
  if (gapStart != null) gaps.push({ start: (gapStart - start) / duration * 100, end: ((times.at(-1) ?? end) - start) / duration * 100 });
  return { paths, points, gaps };
}

export function chartLayout(series: MetricSeries[], unit: string, width: number) {
  const samples = series.flatMap(s => s.points);
  const times = [...new Set(samples.map(p => Date.parse(p.time)).filter(Number.isFinite))].sort((a, b) => a - b);
  const factor = unit === "bytes" ? 1_048_576 : 1;
  const values = samples.filter(p => p.value != null && Number.isFinite(p.value) && Number.isFinite(Date.parse(p.time))).map(p => p.value! / factor);
  let minimum = 0, maximum = 100, step = 25;
  if (unit === "boolean") { maximum = 1; step = 1; }
  else if (unit !== "percent") {
    minimum = Math.min(0, ...values);
    maximum = Math.max(0, ...values);
    const rough = (maximum - minimum || 1) / 4;
    const power = 10 ** Math.floor(Math.log10(rough));
    step = ([1, 2, 5, 10].find(n => n * power >= rough) ?? 10) * power;
    minimum = Math.floor(minimum / step) * step;
    maximum = Math.ceil(maximum / step) * step;
    if (minimum === maximum) maximum = minimum + step * 4;
  }
  const ticks = Array.from({ length: Math.round((maximum - minimum) / step) + 1 }, (_, i) => {
    const value = minimum + i * step;
    return { value: value * factor, y: 100 - (value - minimum) / (maximum - minimum) * 100, label: value.toLocaleString("ru-RU", { maximumFractionDigits: 6, notation: Math.abs(value) >= 100_000 ? "compact" : "standard" }) };
  });
  const left = Math.max(48, ...ticks.map(tick => tick.label.length * 7 + 14));
  const domain = { start: times[0] ?? 0, end: times.at(-1) ?? 0, minimum: minimum * factor, maximum: maximum * factor };
  const timeTickCount = times.length < 2 ? times.length : width < 520 ? 3 : 5;
  const timeTicks = Array.from({ length: timeTickCount }, (_, i) => ({ time: domain.start + (domain.end - domain.start) * i / Math.max(1, timeTickCount - 1), x: timeTickCount === 1 ? 0 : i / (timeTickCount - 1) * 100 }));
  return { width, left, top: 28, plotWidth: Math.max(1, width - left - 16), plotHeight: 240, height: 310, ticks, timeTicks, times,
    series: series.map(s => ({ name: s.name, ...chartGeometry(s.points, domain) })) };
}

export const MetricChart = component$<{ series: MetricSeries[]; unit: string; title: string }>(({ series, unit, title }) => {
  const container = useSignal<HTMLElement>();
  const width = useSignal(320);
  const selected = useSignal<number | null>(null);
  // eslint-disable-next-line qwik/no-use-visible-task
  useVisibleTask$(({ cleanup }) => {
    const element = container.value;
    if (!element) return;
    width.value = element.getBoundingClientRect().width || 320;
    const observer = new ResizeObserver(entries => { width.value = entries[0]?.contentRect.width || 320; });
    observer.observe(element);
    cleanup(() => observer.disconnect());
  });
  const layout = chartLayout(series, unit, width.value);
  const available = layout.series.some(s => s.points.length > 0);
  const hasGaps = layout.series.some(s => s.gaps.length > 0);
  const inspectionIndex = selected.value == null ? Math.max(0, layout.times.length - 1) : Math.min(selected.value, Math.max(0, layout.times.length - 1));
  const inspectionTime = layout.times[inspectionIndex];
  const colors = ["var(--info)", "var(--success)"];
  const unitLabel = unit === "percent" ? "%" : unit === "bytes" ? "МиБ" : unit === "milliseconds" ? "мс" : unit === "boolean" ? "0 — нет · 1 — да" : "/ мин";
  return <figure ref={container} class="min-w-0">
    {layout.times.length > 0 ? <svg role="img" aria-label={`${title}: история значений. Разрывы означают отсутствие данных.`} viewBox={`0 0 ${layout.width} ${layout.height}`} width="100%" height={layout.height} class="mt-3 block overflow-visible text-muted">
      <title>{title}</title>
      <desc>Числовая шкала слева, время внизу. Затенённые интервалы: нет измерений. Показания доступны под графиком и через выбор времени.</desc>
      <text x={layout.left} y="14" fill="currentColor" font-size="11">{unitLabel}</text>
      {layout.series.map((s, i) => <g key={`gaps-${i}`}>{s.gaps.map((gap, j) => <rect key={j} x={layout.left + gap.start / 100 * layout.plotWidth} y={layout.top} width={Math.max(0, (gap.end - gap.start) / 100 * layout.plotWidth)} height={layout.plotHeight} fill={colors[i]} opacity="0.08"><title>{`${s.name}: Нет измерений`}</title></rect>)}</g>)}
      {layout.ticks.map(tick => <g key={tick.value}>
        <line x1={layout.left} x2={layout.left + layout.plotWidth} y1={layout.top + tick.y / 100 * layout.plotHeight} y2={layout.top + tick.y / 100 * layout.plotHeight} stroke="currentColor" opacity="0.18" />
        <text x={layout.left - 10} y={layout.top + tick.y / 100 * layout.plotHeight + 4} text-anchor="end" fill="currentColor" font-size="11">{tick.label}</text>
      </g>)}
      {layout.timeTicks.map((tick, i) => <g key={tick.time}>
        <line x1={layout.left + tick.x / 100 * layout.plotWidth} x2={layout.left + tick.x / 100 * layout.plotWidth} y1={layout.top} y2={layout.top + layout.plotHeight} stroke="currentColor" opacity="0.1" />
        <text x={layout.left + tick.x / 100 * layout.plotWidth} y={layout.top + layout.plotHeight + 20} text-anchor={i === 0 ? "start" : i === layout.timeTicks.length - 1 ? "end" : "middle"} fill="currentColor" font-size="11">
          {new Date(tick.time).toLocaleTimeString("ru-RU", { hour: "2-digit", minute: "2-digit" })}
          {layout.times.at(-1)! - layout.times[0] >= 86_400_000 && <tspan x={layout.left + tick.x / 100 * layout.plotWidth} dy="14">{new Date(tick.time).toLocaleDateString("ru-RU", { day: "2-digit", month: "2-digit" })}</tspan>}
        </text>
      </g>)}
      {layout.series.map((s, i) => <g key={i}>
        <g transform={`translate(${layout.left} ${layout.top}) scale(${layout.plotWidth / 100} ${layout.plotHeight / 100})`}>{s.paths.map((path, j) => <path key={j} d={path} fill="none" stroke={colors[i]} stroke-width="2" vector-effect="non-scaling-stroke" />)}</g>
        {s.points.map((point, j) => <circle key={j} cx={layout.left + point.x / 100 * layout.plotWidth} cy={layout.top + point.y / 100 * layout.plotHeight} r={Date.parse(point.sample.time) === inspectionTime ? 4 : 2.5} fill={colors[i]}><title>{`${s.name} · ${new Date(point.sample.time).toLocaleString("ru-RU")}: ${formatMetric(point.sample.value, unit)}`}</title></circle>)}
      </g>)}
    </svg> : <p class="py-8 text-sm text-muted">Нет данных за выбранный период</p>}
    <figcaption class="space-y-2 text-xs text-muted">
      {layout.times.length > 0 && <p>{new Date(layout.times[0]).toLocaleString("ru-RU")} — {new Date(layout.times.at(-1)!).toLocaleString("ru-RU")}</p>}
      {hasGaps && <p><span class="mr-2 inline-block h-2 w-3 bg-current opacity-20" />Нет измерений · затенённые интервалы</p>}
      {!available && layout.times.length > 0 && <p>Нет данных за выбранный период</p>}
      {available && <>
        <div class="flex flex-wrap gap-x-4 gap-y-1">{series.map((s, i) => {
          const point = s.points.find(p => Date.parse(p.time) === inspectionTime);
          return <p key={i}><span class="mr-2 inline-block h-2 w-2 rounded-full" style={{ backgroundColor: colors[i] }} />{s.name}: <strong class="font-medium text-text">{point?.value == null ? "Нет измерений" : formatMetric(point.value, unit)}</strong></p>;
        })}</div>
        <label class="block">{selected.value == null ? "Последнее измерение" : "Выбранное измерение"}: {new Date(inspectionTime).toLocaleString("ru-RU")}
          <input type="range" class="block h-8 w-full accent-primary" min="0" max={Math.max(0, layout.times.length - 1)} step="1" value={inspectionIndex} disabled={layout.times.length < 2} aria-label={`Время измерения: ${title}`} aria-valuetext={new Date(inspectionTime).toLocaleString("ru-RU")} onInput$={(_, element) => { selected.value = Number(element.value); }} />
        </label>
      </>}
    </figcaption>
  </figure>;
});
