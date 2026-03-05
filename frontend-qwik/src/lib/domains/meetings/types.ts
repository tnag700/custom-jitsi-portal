export interface Meeting {
  meetingId: string;
  roomId: string;
  title: string;
  description: string | null;
  meetingType: string;
  configSetId: string;
  status: "scheduled" | "canceled" | "ended";
  startsAt: string;
  endsAt: string;
  allowGuests: boolean;
  recordingEnabled: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface PagedMeetingResponse {
  content: Meeting[];
  page: number;
  pageSize: number;
  totalElements: number;
  totalPages: number;
}

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

export interface ParticipantAssignment {
  assignmentId: string;
  meetingId: string;
  subjectId: string;
  role: "host" | "moderator" | "participant";
  assignedBy: string;
  assignedAt: string;
  createdAt: string;
  updatedAt: string;
  fullName: string | null;
  organization: string | null;
  position: string | null;
}

export interface AssignParticipantRequest {
  subjectId: string;
  role: "host" | "moderator" | "participant";
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
