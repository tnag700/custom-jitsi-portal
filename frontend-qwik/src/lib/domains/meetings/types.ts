export type {
  MeetingResponse as Meeting,
  PagedMeetingResponse,
  ParticipantAssignmentResponse as ParticipantAssignment,
  UserProfileSummaryResponse as UserProfileSummary,
} from "../../shared/api";

export interface CreateMeetingRequest {
  title: string;
  description?: string;
  meetingType: string;
  startsAt: string;
  endsAt: string;
  allowGuests?: boolean;
  recordingEnabled?: boolean;
}

export interface UpdateMeetingRequest {
  title?: string;
  description?: string;
  meetingType?: string;
  startsAt?: string;
  endsAt?: string;
  allowGuests?: boolean;
  recordingEnabled?: boolean;
}

export interface AssignParticipantRequest {
  subjectId: string;
  role: "host" | "moderator" | "participant";
}

export interface BulkAssignParticipantsRequest {
  participants: Array<{
    subjectId: string;
    role?: "host" | "moderator" | "participant";
  }>;
  defaultRole?: "host" | "moderator" | "participant";
}

export interface UpdateParticipantRoleRequest {
  role: "host" | "moderator" | "participant";
}

export interface MeetingErrorPayload {
  title: string;
  detail: string;
  errorCode: string;
  traceId?: string;
}
