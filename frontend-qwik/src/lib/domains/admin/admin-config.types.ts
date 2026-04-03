import { z } from "zod";
import {
  configSetResponseSchema,
} from "../../shared/api";

export const adminConfigEnvironmentSchema = z.enum(["DEV", "TEST", "PROD"]);

export const adminConfigSetCapabilitySchema = z.object({
  role: z.string(),
  canMutate: z.boolean(),
  reason: z.string().nullable().optional().transform((value) => value ?? null),
});

export const adminConfigSetRolloutSummarySchema = z.object({
  rolloutId: z.string().nullable().optional().transform((value) => value ?? null),
  configSetId: z.string().nullable().optional().transform((value) => value ?? null),
  previousConfigSetId: z.string().nullable().optional().transform((value) => value ?? null),
  tenantId: z.string().nullable().optional().transform((value) => value ?? null),
  environmentType: z.string().nullable().optional().transform((value) => value ?? null),
  status: z.string().nullable().optional().transform((value) => value ?? null),
  validationErrors: z.string().nullable().optional().transform((value) => value ?? null),
  startedAt: z.string().nullable().optional().transform((value) => value ?? null),
  completedAt: z.string().nullable().optional().transform((value) => value ?? null),
  actorId: z.string().nullable().optional().transform((value) => value ?? null),
});

export const adminConfigCompatibilityMismatchSchema = z.object({
  code: z.string().nullable().optional().transform((value) => value ?? null),
  message: z.string().nullable().optional().transform((value) => value ?? null),
  expected: z.string().nullable().optional().transform((value) => value ?? null),
  actual: z.string().nullable().optional().transform((value) => value ?? null),
});

export const adminConfigCompatibilitySchema = z.object({
  status: z.string().nullable().optional().transform((value) => value ?? null),
  checkedAt: z.string().nullable().optional().transform((value) => value ?? null),
  traceId: z.string().nullable().optional().transform((value) => value ?? null),
  mismatches: z.array(adminConfigCompatibilityMismatchSchema).optional().transform((value) => value ?? []),
});

export const adminConfigSetSummarySchema = configSetResponseSchema.extend({
  latestRollout: adminConfigSetRolloutSummarySchema.nullable().optional().transform((value) => value ?? null),
  compatibilityStatus: z.string().nullable().optional().transform((value) => value ?? null),
  compatibilityTraceId: z.string().nullable().optional().transform((value) => value ?? null),
  capability: adminConfigSetCapabilitySchema.optional(),
});

export const adminConfigSetDetailSchema = configSetResponseSchema.extend({
  compatibility: adminConfigCompatibilitySchema.nullable().optional().transform((value) => value ?? null),
  latestRollout: adminConfigSetRolloutSummarySchema.nullable().optional().transform((value) => value ?? null),
});

export const adminConfigSetPageSchema = z.object({
  items: z.array(adminConfigSetSummarySchema),
  page: z.number(),
  pageSize: z.number(),
  totalElements: z.number(),
  totalPages: z.number(),
});

export const adminConfigOperationResultSchema = z.object({
  kind: z.enum(["save", "compatibility", "rollout", "rollback"]),
  status: z.string(),
  message: z.string(),
  traceId: z.string().nullable().optional().transform((value) => value ?? null),
  actorId: z.string().nullable().optional().transform((value) => value ?? null),
});

export const adminConfigSetFormSchema = z.object({
  name: z.string().min(1, "Название обязательно").max(255, "Макс. 255 символов"),
  environmentType: adminConfigEnvironmentSchema,
  issuer: z.string().min(1, "Issuer обязателен"),
  audience: z.string().min(1, "Audience обязателен"),
  algorithm: z.string().min(1, "Algorithm обязателен"),
  roleClaim: z.string().max(255, "Макс. 255 символов").optional(),
  signingSecret: z.string().optional(),
  jwksUri: z.string().optional(),
  accessTtlMinutes: z.coerce.number().int().min(1, "TTL должен быть больше 0"),
  refreshTtlMinutes: z.coerce.number().int().min(1, "Refresh TTL должен быть больше 0").optional(),
  meetingsServiceUrl: z.string().url("Укажите корректный URL meetings service"),
});

export type AdminConfigEnvironment = z.infer<typeof adminConfigEnvironmentSchema>;
export type AdminConfigSetCapability = z.infer<typeof adminConfigSetCapabilitySchema>;
export type AdminConfigSetRolloutSummary = z.infer<typeof adminConfigSetRolloutSummarySchema>;
export type AdminConfigCompatibilityMismatch = z.infer<typeof adminConfigCompatibilityMismatchSchema>;
export type AdminConfigCompatibility = z.infer<typeof adminConfigCompatibilitySchema>;
export type AdminConfigSetSummary = z.infer<typeof adminConfigSetSummarySchema>;
export type AdminConfigSetDetail = z.infer<typeof adminConfigSetDetailSchema>;
export type AdminConfigSetPage = z.infer<typeof adminConfigSetPageSchema>;
export type AdminConfigOperationResult = z.infer<typeof adminConfigOperationResultSchema>;
export type AdminConfigSetForm = z.infer<typeof adminConfigSetFormSchema>;

export function normalizeConfigSetPayload(data: unknown): Record<string, unknown> {
  const record = typeof data === "object" && data !== null ? { ...(data as Record<string, unknown>) } : {};
  if (record.roleClaim == null) {
    record.roleClaim = undefined;
  }
  if (record.signingSecret == null) {
    record.signingSecret = undefined;
  }
  if (record.jwksUri == null) {
    record.jwksUri = undefined;
  }
  if (record.refreshTtlMinutes == null) {
    record.refreshTtlMinutes = undefined;
  }
  return record;
}

export function mapPagedConfigSetResponse(data: unknown): AdminConfigSetPage {
  const page = z.object({
    content: z.array(z.unknown()),
    page: z.number(),
    pageSize: z.number(),
    totalElements: z.number(),
    totalPages: z.number(),
  }).parse(data);
  return adminConfigSetPageSchema.parse({
    items: page.content.map((item) => ({
      ...configSetResponseSchema.parse(normalizeConfigSetPayload(item)),
      latestRollout: null,
      compatibilityStatus: null,
      compatibilityTraceId: null,
    })),
    page: page.page,
    pageSize: page.pageSize,
    totalElements: page.totalElements,
    totalPages: page.totalPages,
  });
}