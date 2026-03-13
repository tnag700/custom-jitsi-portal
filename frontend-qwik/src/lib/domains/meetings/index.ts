export type {
  Meeting,
  PagedMeetingResponse,
  CreateMeetingRequest,
  UpdateMeetingRequest,
  ParticipantAssignment,
  AssignParticipantRequest,
  BulkAssignParticipantsRequest,
  UpdateParticipantRoleRequest,
  MeetingErrorPayload,
  UserProfileSummary,
} from "./types";

export {
  fetchMeetings,
  createMeeting,
  updateMeeting,
  cancelMeeting,
  MeetingServiceError,
  adaptMeetingProblemDetails,
} from "./meetings.service";

export {
  fetchParticipants,
  assignParticipant,
  bulkAssignParticipants,
  searchUsers,
  updateParticipantRole,
  unassignParticipant,
} from "./participants.service";

export {
  createMeetingSchema,
  updateMeetingSchema,
  assignParticipantSchema,
  bulkAssignParticipantsSchema,
  updateParticipantRoleSchema,
} from "./meetings.zod";

export { MeetingCard } from "./components/MeetingCard";
export { MeetingForm } from "./components/MeetingForm";
export { MeetingList } from "./components/MeetingList";
export { ParticipantPanel } from "./components/ParticipantPanel";
