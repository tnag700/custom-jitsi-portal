import { z } from "zod";

export interface AdminDashboardErrorPayload {
  title: string;
  detail: string;
  errorCode: string;
  traceId?: string;
}

export const adminIncidentListItemSchema = z.object({
  incidentId: z.string(),
  occurredAt: z.string(),
  errorCode: z.string(),
  category: z.string(),
  tenantId: z.string(),
  roomId: z.string().nullable().optional().transform((value) => value ?? null),
  meetingId: z.string().nullable().optional().transform((value) => value ?? null),
  affectedSubjects: z.number(),
  severity: z.string(),
  affectedEntitySummary: z.string(),
  freshnessHint: z.string(),
});

export const adminIncidentSavedViewSchema = z.object({
  token: z.string(),
  label: z.string(),
  summary: z.string(),
});

export const adminIncidentQuickFacetSchema = z.object({
  token: z.string(),
  label: z.string(),
  count: z.number(),
  active: z.boolean(),
});

export const adminIncidentQueueSortSchema = z.object({
  token: z.string(),
  label: z.string(),
  direction: z.string(),
});

export const adminIncidentListSchema = z.object({
  period: z.string(),
  environment: z.string(),
  tenantId: z.string(),
  generatedAt: z.string(),
  selectedView: z.string(),
  selectedQuickFacet: z.string().nullable().optional().transform((value) => value ?? null),
  availableViews: z.array(adminIncidentSavedViewSchema),
  quickFacets: z.array(adminIncidentQuickFacetSchema),
  sort: adminIncidentQueueSortSchema,
  pageSize: z.number(),
  offset: z.number(),
  totalElements: z.number(),
  items: z.array(adminIncidentListItemSchema),
});

export const adminIncidentAttemptSchema = z.object({
  occurredAt: z.string(),
  traceId: z.string().nullable().optional().transform((value) => value ?? null),
  correlationId: z.string().nullable().optional().transform((value) => value ?? null),
  subjectDisplay: z.string().nullable().optional().transform((value) => value ?? null),
  subjectIdFilterValue: z.string().nullable().optional().transform((value) => value ?? null),
  role: z.string().nullable().optional().transform((value) => value ?? null),
  diagnosticResult: z.string().nullable().optional().transform((value) => value ?? null),
  roomId: z.string().nullable().optional().transform((value) => value ?? null),
  meetingId: z.string().nullable().optional().transform((value) => value ?? null),
  traceUrl: z.string().nullable().optional().transform((value) => value ?? null),
});

export const adminIncidentSummaryBarSchema = z.object({
  title: z.string(),
  refusalReason: z.string(),
  affectedScope: z.string(),
  operationalStatus: z.string(),
  timeWindow: z.string(),
  environment: z.string(),
});

export const adminIncidentTimelineEntrySchema = z.object({
  occurredAt: z.string(),
  title: z.string(),
  summary: z.string(),
  subjectDisplay: z.string().nullable().optional().transform((value) => value ?? null),
  role: z.string().nullable().optional().transform((value) => value ?? null),
  traceId: z.string().nullable().optional().transform((value) => value ?? null),
  correlationId: z.string().nullable().optional().transform((value) => value ?? null),
  roomId: z.string().nullable().optional().transform((value) => value ?? null),
  meetingId: z.string().nullable().optional().transform((value) => value ?? null),
});

export const adminIncidentEmptyStateSchema = z.object({
  title: z.string(),
  detail: z.string(),
  nextActionLabel: z.string(),
  nextActionTarget: z.string(),
});

export const adminIncidentEvidenceBlockSchema = z.object({
  kind: z.string(),
  title: z.string(),
  status: z.string(),
  summary: z.string().nullable().optional().transform((value) => value ?? null),
  detail: z.string(),
  traceId: z.string().nullable().optional().transform((value) => value ?? null),
  correlationId: z.string().nullable().optional().transform((value) => value ?? null),
  traceUrl: z.string().nullable().optional().transform((value) => value ?? null),
  emptyState: adminIncidentEmptyStateSchema.nullable().optional().transform((value) => value ?? null),
});

export const adminIncidentRelatedLinkSchema = z.object({
  kind: z.string(),
  label: z.string(),
  environment: z.string().nullable().optional().transform((value) => value ?? null),
  subjectId: z.string().nullable().optional().transform((value) => value ?? null),
  roomId: z.string().nullable().optional().transform((value) => value ?? null),
  meetingId: z.string().nullable().optional().transform((value) => value ?? null),
  traceId: z.string().nullable().optional().transform((value) => value ?? null),
  externalUrl: z.string().nullable().optional().transform((value) => value ?? null),
});

export const adminIncidentNextActionSchema = z.object({
  kind: z.string(),
  label: z.string(),
  detail: z.string(),
  target: z.string(),
  externalUrl: z.string().nullable().optional().transform((value) => value ?? null),
});

export const adminIncidentTicketingStateSchema = z.object({
  available: z.boolean(),
  ticketKey: z.string().nullable().optional().transform((value) => value ?? null),
  ticketUrl: z.string().nullable().optional().transform((value) => value ?? null),
  status: z.string(),
});

export const adminIncidentCoordinationAuditEntrySchema = z.object({
  occurredAt: z.string(),
  actorId: z.string(),
  actionType: z.string(),
  traceId: z.string().nullable().optional().transform((value) => value ?? null),
  fromState: z.string(),
  toState: z.string(),
});

export const adminIncidentCoordinationSchema = z.object({
  enabled: z.boolean(),
  availability: z.string(),
  explanation: z.string(),
  owner: z.string().nullable().optional().transform((value) => value ?? null),
  workflowStatus: z.string(),
  ticketReference: z.string().nullable().optional().transform((value) => value ?? null),
  ticketStatus: z.string(),
  ticketUrl: z.string().nullable().optional().transform((value) => value ?? null),
  history: z.array(adminIncidentCoordinationAuditEntrySchema),
});

export const adminIncidentDetailSchema = z.object({
  incidentId: z.string(),
  tenantId: z.string(),
  environment: z.string(),
  errorCode: z.string(),
  category: z.string(),
  severity: z.string(),
  summary: z.string(),
  startedAt: z.string(),
  endedAt: z.string(),
  affectedAttempts: z.array(adminIncidentAttemptSchema),
  summaryBar: adminIncidentSummaryBarSchema,
  timeline: z.array(adminIncidentTimelineEntrySchema),
  evidence: z.array(adminIncidentEvidenceBlockSchema),
  relatedLinks: z.array(adminIncidentRelatedLinkSchema),
  nextActions: z.array(adminIncidentNextActionSchema),
  coordination: adminIncidentCoordinationSchema,
  ticketing: adminIncidentTicketingStateSchema,
});

export const adminIncidentSearchCandidateSchema = z.object({
  incidentId: z.string(),
  occurredAt: z.string(),
  errorCode: z.string(),
  meetingId: z.string().nullable().optional().transform((value) => value ?? null),
});

export const adminIncidentSearchSchema = z.object({
  outcome: z.string(),
  incidentId: z.string().nullable().optional().transform((value) => value ?? null),
  detailUrl: z.string().nullable().optional().transform((value) => value ?? null),
  message: z.string().nullable().optional().transform((value) => value ?? null),
  candidates: z.array(adminIncidentSearchCandidateSchema),
});

export const adminIncidentTicketSchema = z.object({
  available: z.boolean(),
  created: z.boolean(),
  ticketKey: z.string().nullable().optional().transform((value) => value ?? null),
  ticketUrl: z.string().nullable().optional().transform((value) => value ?? null),
  summary: z.string(),
  message: z.string().nullable().optional().transform((value) => value ?? null),
});

export const adminServiceStatusSchema = z.object({
  key: z.string(),
  label: z.string(),
  status: z.string(),
  detail: z.string(),
  handoff: z.object({
    environment: z.string(),
    period: z.string(),
    severity: z.string(),
    errorCode: z.string().nullable().optional().transform((value) => value ?? null),
    category: z.string().nullable().optional().transform((value) => value ?? null),
    roomId: z.string().nullable().optional().transform((value) => value ?? null),
    meetingId: z.string().nullable().optional().transform((value) => value ?? null),
    incidentId: z.string().nullable().optional().transform((value) => value ?? null),
  }),
});

export const adminDashboardHandoffSchema = adminServiceStatusSchema.shape.handoff;

export const adminPriorityBannerSchema = z.object({
  active: z.boolean(),
  severity: z.string(),
  headline: z.string(),
  summary: z.string(),
  actionLabel: z.string(),
  handoff: adminDashboardHandoffSchema,
});

export const adminDegradationSummarySchema = z.object({
  id: z.string(),
  title: z.string(),
  summary: z.string(),
  severity: z.string(),
  actionLabel: z.string(),
  handoff: adminDashboardHandoffSchema,
});

export const adminLatestSpikeSchema = z.object({
  errorCode: z.string(),
  category: z.string().nullable().optional().transform((value) => value ?? null),
  count: z.number(),
  summary: z.string(),
  handoff: adminDashboardHandoffSchema,
});

export const adminAffectedScopeSummarySchema = z.object({
  scopeType: z.string(),
  scopeValue: z.string(),
  affectedAttempts: z.number(),
  summary: z.string(),
  handoff: adminDashboardHandoffSchema,
});

export const adminSafeStateActionSchema = z.object({
  label: z.string(),
  href: z.string(),
});

export const adminResolvedSpikeSummarySchema = z.object({
  label: z.string(),
  detail: z.string(),
});

export const adminSafeStateSummarySchema = z.object({
  stable: z.boolean(),
  headline: z.string(),
  summary: z.string(),
  actions: z.array(adminSafeStateActionSchema),
  recentResolvedSpikes: z.array(adminResolvedSpikeSummarySchema),
});

export const adminEntityFilterSchema = z.object({
  roomId: z.string().nullable().optional().transform((value) => value ?? null),
  meetingId: z.string().nullable().optional().transform((value) => value ?? null),
});

export const adminDashboardSummarySchema = z.object({
  period: z.string(),
  environment: z.string(),
  tenantId: z.string(),
  generatedAt: z.string(),
  traceId: z.string(),
  priorityBanner: adminPriorityBannerSchema,
  topDegradations: z.array(adminDegradationSummarySchema),
  keyServiceStatuses: z.array(adminServiceStatusSchema),
  latestSpikes: z.array(adminLatestSpikeSchema),
  affectedScopeSummary: z.array(adminAffectedScopeSummarySchema),
  safeStateSummary: adminSafeStateSummarySchema,
  entityFilter: adminEntityFilterSchema,
  sampleWindowLimited: z.boolean(),
});

export const adminRecentSampleSchema = z.object({
  occurredAt: z.string(),
  roomId: z.string().nullable().optional().transform((value) => value ?? null),
  meetingId: z.string().nullable().optional().transform((value) => value ?? null),
  subjectId: z.string().nullable().optional().transform((value) => value ?? null),
  traceId: z.string().nullable().optional().transform((value) => value ?? null),
  traceUrl: z.string().nullable().optional().transform((value) => value ?? null),
  errorCode: z.string().nullable().optional().transform((value) => value ?? null),
  reasonCategory: z.string().nullable().optional().transform((value) => value ?? null),
  userMessage: z.string(),
});

export const adminDashboardDrillDownSchema = z.object({
  period: z.string(),
  environment: z.string(),
  tenantId: z.string(),
  generatedAt: z.string(),
  selectionType: z.string(),
  selectionValue: z.string(),
  entityFilter: adminEntityFilterSchema,
  failureCount: z.number(),
  recentSamples: z.array(adminRecentSampleSchema),
  sampleWindowLimited: z.boolean(),
});

export const adminRoleHistoryEntrySchema = z.object({
  occurredAt: z.string(),
  actionType: z.string(),
  actionLabel: z.string(),
  oldRole: z.string().nullable().optional().transform((value) => value ?? null),
  newRole: z.string().nullable().optional().transform((value) => value ?? null),
  subjectLabel: z.string().nullable().optional().transform((value) => value ?? null),
  subjectReference: z.string().nullable().optional().transform((value) => value ?? null),
  actorLabel: z.string().nullable().optional().transform((value) => value ?? null),
  actorReference: z.string().nullable().optional().transform((value) => value ?? null),
  tenantId: z.string(),
  environment: z.string(),
  roomId: z.string().nullable().optional().transform((value) => value ?? null),
  meetingId: z.string().nullable().optional().transform((value) => value ?? null),
  traceId: z.string().nullable().optional().transform((value) => value ?? null),
});

export const adminRoleHistorySchema = z.object({
  tenantId: z.string(),
  environment: z.string(),
  generatedAt: z.string(),
  page: z.number(),
  pageSize: z.number(),
  totalElements: z.number(),
  totalPages: z.number(),
  content: z.array(adminRoleHistoryEntrySchema),
});

export type AdminDashboardSummary = z.infer<typeof adminDashboardSummarySchema>;
export type AdminDashboardDrillDown = z.infer<typeof adminDashboardDrillDownSchema>;
export type AdminIncidentList = z.infer<typeof adminIncidentListSchema>;
export type AdminIncidentDetail = z.infer<typeof adminIncidentDetailSchema>;
export type AdminIncidentCoordination = z.infer<typeof adminIncidentCoordinationSchema>;
export type AdminIncidentSearch = z.infer<typeof adminIncidentSearchSchema>;
export type AdminIncidentTicket = z.infer<typeof adminIncidentTicketSchema>;
export type AdminRoleHistory = z.infer<typeof adminRoleHistorySchema>;