import { describe, expect, it } from "vitest";
import { chartGeometry, chartLayout } from "../lib/domains/admin/components/MetricChart";

describe("metric SVG geometry", () => {
  it("keeps leading and trailing outages on the time axis", () => {
    const points = [null, null, 20, 40, null].map((value, i) => ({ time: new Date(i * 60_000).toISOString(), value }));
    expect(chartGeometry(points).points.map(point => point.x)).toEqual([50, 75]);
  });
  it("breaks the line at gaps and renders a single or constant sample without NaN", () => {
    const points = [1, 1, null, 1].map((value, i) => ({ time: new Date(i * 15_000).toISOString(), value }));
    const result = chartGeometry(points);
    expect(result.paths).toHaveLength(2);
    expect(result.points).toHaveLength(3);
    expect(JSON.stringify(result)).not.toMatch(/NaN|Infinity/);
    expect(chartGeometry(points.slice(0, 1)).points).toHaveLength(1);
    expect(chartGeometry([{ time: "invalid", value: Infinity }]).points).toHaveLength(0);
    const full = chartGeometry(Array.from({ length: 300 }, (_, i) => ({ time: new Date(i * 15_000).toISOString(), value: i })));
    expect(full.points).toHaveLength(300);
    expect(full.points.every(p => p.x >= 0 && p.x <= 100 && p.y >= 0 && p.y <= 100)).toBe(true);
  });
  it("keeps percentage and boolean scales stable instead of exaggerating small changes", () => {
    const points = [20, 40].map((value, i) => ({ time: new Date(i * 60_000).toISOString(), value }));
    const percent = chartLayout([{ name: "CPU", points }], "percent", 320);
    expect(percent.ticks.map(tick => tick.value)).toEqual([0, 25, 50, 75, 100]);
    expect(percent.series[0].points.map(point => point.y)).toEqual([80, 60]);
    const boolean = chartLayout([{ name: "Backend", points: points.map(p => ({ ...p, value: 1 })) }], "boolean", 320);
    expect(boolean.ticks.map(tick => tick.value)).toEqual([0, 1]);
    expect(boolean.series[0].points.map(point => point.y)).toEqual([0, 0]);
  });
  it("shares numeric scales between series and leaves readable pixel margins on narrow charts", () => {
    const layout = chartLayout([
      { name: "A", points: [{ time: new Date(0).toISOString(), value: 10 }] },
      { name: "B", points: [{ time: new Date(60_000).toISOString(), value: 40 }] },
    ], "milliseconds", 320);
    expect(layout.series.map(s => s.points[0].y)).toEqual([75, 0]);
    expect(layout.series.map(s => s.points[0].x)).toEqual([0, 100]);
    expect(layout.left).toBeGreaterThanOrEqual(48);
    expect(layout.plotWidth).toBeGreaterThanOrEqual(220);
    expect(layout.plotHeight).toBeGreaterThanOrEqual(240);
    expect(layout.timeTicks.at(0)?.time).toBe(0);
    expect(layout.timeTicks.at(-1)?.time).toBe(60_000);
    expect(layout.ticks.every(tick => tick.label.length > 0)).toBe(true);
  });
  it("marks leading, middle and trailing missing intervals while keeping their timestamps", () => {
    const points = [null, 20, null, 40, null].map((value, i) => ({ time: new Date(i * 60_000).toISOString(), value }));
    const layout = chartLayout([{ name: "CPU", points }], "percent", 320);
    expect(layout.series[0].paths).toHaveLength(2);
    expect(layout.series[0].gaps).toEqual([{ start: 0, end: 25 }, { start: 25, end: 75 }, { start: 75, end: 100 }]);
    expect(layout.timeTicks.at(0)?.time).toBe(0);
    expect(layout.timeTicks.at(-1)?.time).toBe(240_000);
    const unavailable = chartLayout([{ name: "CPU", points: points.map(p => ({ ...p, value: null })) }], "percent", 320);
    expect(unavailable.series[0].points).toEqual([]);
    expect(unavailable.series[0].gaps).toEqual([{ start: 0, end: 100 }]);
    expect(unavailable.timeTicks.at(-1)?.time).toBe(240_000);
  });
});
