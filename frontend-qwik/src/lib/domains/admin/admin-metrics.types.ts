import { z } from "zod";

export const metricPeriodSchema = z.enum(["15m", "1h", "6h", "24h", "7d"]);
export const metricDescriptorSchema = z.object({
  id: z.string(), title: z.string(), description: z.string(),
  unit: z.enum(["percent", "bytes", "per_minute", "milliseconds", "boolean"]),
  scope: z.literal("SYSTEM"), views: z.array(z.enum(["card", "line"])).min(1).max(2),
}).strict();
export const dashboardSchema = z.object({
  revision: z.number().int().nonnegative(), period: metricPeriodSchema,
  widgets: z.array(z.object({ metricId: z.string(), view: z.enum(["card", "line"]) }).strict()).max(12),
}).strict();
export const snapshotSchema = z.object({
  generatedAt: z.string().datetime(), metrics: z.array(z.object({
    id: z.string(), value: z.number().finite().nullable(),
    state: z.enum(["ok", "no_data", "no_traffic", "stale", "unavailable", "partial"]),
    measuredAt: z.string().datetime().nullable(), series: z.array(z.object({
      name: z.string(), points: z.array(z.object({ time: z.string().datetime(), value: z.number().finite().nullable() }).strict()).max(300),
    }).strict()).max(2),
  }).strict()).max(12),
}).strict();
export type MetricDescriptor = z.infer<typeof metricDescriptorSchema>;
export type Dashboard = z.infer<typeof dashboardSchema>;
export type MetricsSnapshot = z.infer<typeof snapshotSchema>;
export type MetricSeries = MetricsSnapshot["metrics"][number]["series"][number];
export type MetricPoint = MetricSeries["points"][number];
