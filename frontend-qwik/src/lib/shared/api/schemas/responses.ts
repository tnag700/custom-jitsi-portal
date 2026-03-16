import { z } from "zod";

export const problemDetailSchema = z.object({
  type: z.string().optional(),
  title: z.string().optional(),
  status: z.number().optional(),
  detail: z.string().optional(),
  instance: z.string().optional(),
  errorCode: z.string().optional(),
  traceId: z.string().optional(),
  properties: z
    .object({
      errorCode: z.string().optional(),
      traceId: z.string().optional(),
    })
    .optional(),
});

export const roomResponseSchema = z.object({
  roomId: z.string(),
  name: z.string(),
  description: z.string().nullable().optional().transform((value) => value ?? null),
  tenantId: z.string(),
  configSetId: z.string(),
  status: z.enum(["active", "closed"]),
  createdAt: z.string(),
  updatedAt: z.string(),
});

export const pagedRoomResponseSchema = z.object({
  content: z.array(roomResponseSchema),
  page: z.number(),
  pageSize: z.number(),
  totalElements: z.number(),
  totalPages: z.number(),
});

export const meetingResponseSchema = z.object({
  meetingId: z.string(),
  roomId: z.string(),
  title: z.string(),
  description: z.string().nullable().optional().transform((value) => value ?? null),
  meetingType: z.string(),
  configSetId: z.string(),
  status: z.enum(["scheduled", "canceled", "ended"]),
  startsAt: z.string(),
  endsAt: z.string(),
  allowGuests: z.boolean(),
  recordingEnabled: z.boolean(),
  createdAt: z.string(),
  updatedAt: z.string(),
});

export const pagedMeetingResponseSchema = z.object({
  content: z.array(meetingResponseSchema),
  page: z.number(),
  pageSize: z.number(),
  totalElements: z.number(),
  totalPages: z.number(),
});

export const inviteResponseSchema = z.object({
  id: z.string(),
  meetingId: z.string(),
  token: z.string(),
  role: z.enum(["participant", "moderator"]),
  recipientEmail: z.string().optional(),
  recipientSubjectId: z.string().nullable().optional(),
  maxUses: z.number(),
  usedCount: z.number(),
  expiresAt: z.string().nullable().optional().transform((value) => value ?? null),
  revokedAt: z.string().nullable().optional().transform((value) => value ?? null),
  createdAt: z.string(),
  updatedAt: z.string().optional(),
  createdBy: z.string().optional().transform((value) => value ?? ""),
  valid: z.boolean().optional().transform((value) => value ?? false),
});

export const pagedInviteResponseSchema = z.object({
  content: z.array(inviteResponseSchema).optional(),
  items: z.array(inviteResponseSchema).optional(),
  page: z.number(),
  pageSize: z.number().optional(),
  size: z.number().optional(),
  totalElements: z.number(),
  totalPages: z.number(),
}).refine(
  (d) => d.content !== undefined || d.items !== undefined,
  { message: "pagedInviteResponse: expected either 'content' or 'items' array" },
);

export const userProfileResponseSchema = z.object({
  subjectId: z.string(),
  tenantId: z.string(),
  fullName: z.string(),
  organization: z.string(),
  position: z.string(),
  createdAt: z.string(),
  updatedAt: z.string(),
});

export const upsertProfileRequestSchema = z.object({
  fullName: z.string().min(2),
  organization: z.string().min(2),
  position: z.string().min(2),
});

export const upcomingMeetingCardSchema = z.object({
  meetingId: z.string(),
  title: z.string(),
  startsAt: z.string(),
  roomName: z.string(),
  joinAvailability: z.enum(["available", "scheduled"]),
});

const httpsJoinUrlSchema = z.string().url().refine((value) => {
  try {
    const url = new URL(value);
    return url.protocol === "https:" && url.username === "" && url.password === "";
  } catch {
    return false;
  }
}, "joinUrl must be an HTTPS URL without credentials");

export const meetingAccessTokenResponseSchema = z.object({
  joinUrl: httpsJoinUrlSchema,
  expiresAt: z.string(),
  role: z.string(),
});

export const joinReadinessCheckSchema = z.object({
  key: z.string(),
  status: z.enum(["ok", "warn", "error", "timeout"]),
  headline: z.string(),
  reason: z.string(),
  actions: z.array(z.string()),
  errorCode: z.string().nullable().optional().transform((value) => value ?? null),
  blocking: z.boolean(),
});

export const joinReadinessResponseSchema = z.object({
  status: z.enum(["ready", "degraded", "blocked"]),
  checkedAt: z.string(),
  traceId: z.string().nullable().optional().transform((value) => value ?? null),
  publicJoinUrl: z.string().nullable().optional().transform((value) => value ?? null),
  systemChecks: z.array(joinReadinessCheckSchema),
});

export const safeUserProfileResponseSchema = z.object({
  id: z.string(),
  displayName: z.string(),
  email: z.string(),
  tenant: z.string(),
  claims: z.array(z.string()),
});

export const configSetResponseSchema = z.object({
  configSetId: z.string(),
  name: z.string(),
  tenantId: z.string(),
  environmentType: z.string(),
  issuer: z.string(),
  audience: z.string(),
  algorithm: z.string(),
  roleClaim: z.string().optional(),
  signingSecret: z.string().optional(),
  jwksUri: z.string().optional(),
  accessTtlMinutes: z.number(),
  refreshTtlMinutes: z.number().optional(),
  meetingsServiceUrl: z.string(),
  status: z.string(),
  createdAt: z.string(),
  updatedAt: z.string(),
});

export const pagedConfigSetResponseSchema = z.object({
  content: z.array(configSetResponseSchema),
  page: z.number(),
  pageSize: z.number(),
  totalElements: z.number(),
  totalPages: z.number(),
});

export type ProblemDetail = z.infer<typeof problemDetailSchema>;
export type RoomResponse = z.infer<typeof roomResponseSchema>;
export type PagedRoomResponse = z.infer<typeof pagedRoomResponseSchema>;
export type MeetingResponse = z.infer<typeof meetingResponseSchema>;
export type PagedMeetingResponse = z.infer<typeof pagedMeetingResponseSchema>;
export type InviteResponse = z.infer<typeof inviteResponseSchema>;
export type PagedInviteResponse = z.infer<typeof pagedInviteResponseSchema>;
export type UserProfileResponse = z.infer<typeof userProfileResponseSchema>;
export type UpsertProfileRequest = z.infer<typeof upsertProfileRequestSchema>;
export type UpcomingMeetingCard = z.infer<typeof upcomingMeetingCardSchema>;
export type MeetingAccessTokenResponse = z.infer<typeof meetingAccessTokenResponseSchema>;
export type JoinReadinessCheck = z.infer<typeof joinReadinessCheckSchema>;
export type JoinReadinessResponse = z.infer<typeof joinReadinessResponseSchema>;
export type SafeUserProfileResponse = z.infer<typeof safeUserProfileResponseSchema>;
export type ConfigSetResponse = z.infer<typeof configSetResponseSchema>;
export type PagedConfigSetResponse = z.infer<typeof pagedConfigSetResponseSchema>;
