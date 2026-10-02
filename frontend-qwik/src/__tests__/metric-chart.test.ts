import { describe, expect, it } from "vitest";
import { chartGeometry } from "../lib/domains/admin/components/MetricChart";

describe("metric SVG geometry", () => {
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
});
