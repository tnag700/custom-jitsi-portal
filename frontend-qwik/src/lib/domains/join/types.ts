export interface UpcomingMeetingCard {
  meetingId: string;
  title: string;
  startsAt: string;
  roomName: string;
  joinAvailability: "available" | "scheduled";
}

export interface MeetingAccessTokenResponse {
  joinUrl: string;
  expiresAt: string;
  role: string;
}

export interface JoinReadinessCheck {
  key: string;
  status: "ok" | "warn" | "error" | "timeout";
  headline: string;
  reason: string;
  actions: string[];
  errorCode?: string | null;
  blocking: boolean;
}

export interface JoinReadinessPayload {
  status: "ready" | "degraded" | "blocked";
  checkedAt: string;
  traceId?: string | null;
  publicJoinUrl?: string | null;
  systemChecks: JoinReadinessCheck[];
}

export interface JoinErrorPayload {
  title: string;
  detail: string;
  errorCode: string;
  traceId?: string;
}
