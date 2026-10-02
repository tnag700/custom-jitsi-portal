import { z } from "zod";

export const summarySchema = z.object({
  backendState: z.enum(["working", "problem", "unknown"]),
  cpuPercent: z.number().int().min(0).max(100).nullable(),
  memoryPercent: z.number().int().min(0).max(100).nullable(),
  diskState: z.enum(["sufficient", "low", "unknown"]),
  measuredAt: z.string().datetime().nullable(),
  stale: z.boolean(),
  monitoringConfigured: z.boolean(),
}).strict();

export type Summary = z.infer<typeof summarySchema>;
