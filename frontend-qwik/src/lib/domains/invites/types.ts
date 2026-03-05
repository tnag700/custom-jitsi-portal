export interface Invite {
  id: string;
  meetingId: string;
  token: string;
  role: "participant" | "moderator";
  maxUses: number;
  usedCount: number;
  expiresAt: string | null;
  revokedAt: string | null;
  createdAt: string;
  createdBy: string;
  valid: boolean;
}

export interface PagedInviteResponse {
  content: Invite[];
  page: number;
  pageSize: number;
  totalElements: number;
  totalPages: number;
}

export interface CreateInviteRequest {
  role: "participant" | "moderator";
  maxUses?: number;
  expiresInHours?: number;
}

export interface InviteErrorPayload {
  title: string;
  detail: string;
  errorCode: string;
  traceId?: string;
}

export interface InviteExchangeRequest {
  inviteToken: string;
  displayName?: string;
}

export interface InviteExchangeResponse {
  joinUrl: string;
  expiresAt: string;
  role: string;
  meetingId: string;
}

export interface InviteValidationResponse {
  valid: boolean;
  meetingId: string;
}
