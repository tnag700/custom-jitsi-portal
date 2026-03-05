export type {
  Meeting,
  PagedMeetingResponse,
  CreateMeetingRequest,
  UpdateMeetingRequest,
  ParticipantAssignment,
  AssignParticipantRequest,
  UpdateParticipantRoleRequest,
  MeetingErrorPayload,
} from "./types";

export {
  fetchMeetings,
  createMeeting,
  updateMeeting,
  cancelMeeting,
  MeetingServiceError,
  adaptMeetingProblemDetails,
  baseHeaders,
  mutationHeaders,
} from "./meetings.service";

export {
  fetchParticipants,
  assignParticipant,
  updateParticipantRole,
  unassignParticipant,
} from "./participants.service";

export {
  createMeetingSchema,
  updateMeetingSchema,
  assignParticipantSchema,
  updateParticipantRoleSchema,
} from "./meetings.zod";

export { MeetingCard } from "./components/MeetingCard";
export { MeetingForm } from "./components/MeetingForm";
export { MeetingList } from "./components/MeetingList";
export { ParticipantPanel } from "./components/ParticipantPanel";
