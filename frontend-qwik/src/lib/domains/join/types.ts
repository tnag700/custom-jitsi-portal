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

export interface JoinErrorPayload {
  title: string;
  detail: string;
  errorCode: string;
  traceId?: string;
}
