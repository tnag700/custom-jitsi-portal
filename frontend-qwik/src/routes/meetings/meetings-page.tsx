import { $, component$, useContext, useSignal, useTask$ } from "@qwik.dev/core";
import { useLocation, useNavigate } from "@qwik.dev/router";
import { AuthContext } from "~/lib/domains/auth";
import type { Invite, InviteErrorPayload } from "~/lib/domains/invites";
import type { Meeting, MeetingErrorPayload } from "~/lib/domains/meetings";
import { ParticipantPanel } from "~/lib/domains/meetings";
import { AppToast, useAppToast } from "~/lib/shared";
import { MeetingConfirmationDialogs } from "./components/MeetingConfirmationDialogs";
import { MeetingInvitesWorkspace } from "./components/MeetingInvitesWorkspace";
import { MeetingsOverview } from "./components/MeetingsOverview";
import {
  buildMeetingsHref,
  findMeetingById,
  getActionError,
  getActionValidationFeedback,
  getFirstActionError,
  isSuccessfulAction,
} from "./meetings-page-state";
import {
  useCreateInvite,
  useRevokeInvite,
} from "./invite-actions";
import {
  useActiveRooms,
  useAssignableUsers,
  useInvites,
  useMeetings,
  useParticipants,
} from "./loaders";
import {
  useCancelMeeting,
  useCreateMeeting,
  useUpdateMeeting,
} from "./meeting-actions";
import {
  useBulkAssignParticipants,
  useUnassignParticipant,
  useUpdateParticipantRole,
} from "./participant-actions";

export default component$(() => {
  const authStore = useContext(AuthContext);
  const navigate = useNavigate();
  const location = useLocation();
  const roomsData = useActiveRooms();
  const meetingsData = useMeetings();
  const participantsData = useParticipants();
  const assignableUsersData = useAssignableUsers();
  const invitesData = useInvites();
  const createAction = useCreateMeeting();
  const updateAction = useUpdateMeeting();
  const cancelAction = useCancelMeeting();
  const bulkAssignAction = useBulkAssignParticipants();
  const updateRoleAction = useUpdateParticipantRole();
  const unassignAction = useUnassignParticipant();
  const createInviteAction = useCreateInvite();
  const revokeInviteAction = useRevokeInvite();
  const showCreateForm = useSignal(false);
  const showEditForm = useSignal(false);
  const editingMeeting = useSignal<Meeting | null>(null);
  const confirmingCancel = useSignal<Meeting | null>(null);
  const showInviteForm = useSignal(false);
  const confirmingRevokeInvite = useSignal<Invite | null>(null);
  const showCancelDialog = useSignal(false);
  const showRevokeInviteDialog = useSignal(false);
  const { toast, showToast$, clearToast$ } = useAppToast();
  const selectedRoomId = location.url.searchParams.get("roomId") ?? "";
  const selectedMeeting = findMeetingById(
    meetingsData.value.content,
    location.url.searchParams.get("meetingId") ?? "",
  );
  const selectedInviteMeeting = findMeetingById(
    meetingsData.value.content,
    location.url.searchParams.get("invitesMeetingId") ?? "",
  );
  const createError = getActionError<MeetingErrorPayload>(createAction.value);
  const updateError = getActionError<MeetingErrorPayload>(updateAction.value);
  const createValidationFeedback = getActionValidationFeedback(
    createAction.value,
  );
  const updateValidationFeedback = getActionValidationFeedback(
    updateAction.value,
  );
  const cancelError = getActionError<MeetingErrorPayload>(cancelAction.value);
  const participantError = getFirstActionError<MeetingErrorPayload>(
    bulkAssignAction.value,
    updateRoleAction.value,
    unassignAction.value,
  );
  const inviteError = getFirstActionError<InviteErrorPayload>(
    createInviteAction.value,
    revokeInviteAction.value,
  );

  const openOnNextFrame$ = $((signal: { value: boolean }) => {
    if (typeof window === "undefined") {
      signal.value = true;
      return;
    }

    window.requestAnimationFrame(() => {
      signal.value = true;
    });
  });

  const selectRoom$ = $((roomId: string) => {
    void navigate(buildMeetingsHref(roomId));
  });

  const openParticipants$ = $((meeting: Meeting) => {
    void navigate(
      buildMeetingsHref(meeting.roomId, {
        meetingId: meeting.meetingId,
      }),
    );
  });

  const openInvites$ = $((meeting: Meeting) => {
    void navigate(
      buildMeetingsHref(meeting.roomId, {
        invitesMeetingId: meeting.meetingId,
      }),
    );
  });

  const closeDetail$ = $(() => {
    void navigate(buildMeetingsHref(selectedRoomId));
  });

  const openEdit$ = $((meeting: Meeting) => {
    editingMeeting.value = meeting;
    void openOnNextFrame$(showEditForm);
  });

  const openCancel$ = $((meeting: Meeting) => {
    confirmingCancel.value = meeting;
    void openOnNextFrame$(showCancelDialog);
  });

  const openCreate$ = $(() => {
    showCreateForm.value = true;
  });

  const openInviteCreate$ = $(() => {
    showInviteForm.value = true;
  });

  const closeInviteCreate$ = $(() => {
    showInviteForm.value = false;
  });

  const openRevokeInvite$ = $((invite: Invite) => {
    confirmingRevokeInvite.value = invite;
    void openOnNextFrame$(showRevokeInviteDialog);
  });

  const copyInviteLink$ = $((invite: Invite) => {
    if (typeof window === "undefined") {
      return;
    }

    const url = `${window.location.origin}/invite/${invite.token}/`;
    void navigator.clipboard.writeText(url);
    void showToast$({ message: "Ссылка скопирована", tone: "info" });
  });

  useTask$(async ({ track }) => {
    const result = track(() => createInviteAction.value);
    if (isSuccessfulAction(result)) {
      showInviteForm.value = false;
      await showToast$({ message: "Инвайт создан", tone: "success" });
    }
  });

  useTask$(async ({ track }) => {
    const result = track(() => createAction.value);
    if (isSuccessfulAction(result)) {
      showCreateForm.value = false;
      await showToast$({ message: "Встреча создана", tone: "success" });
    }
  });

  useTask$(async ({ track }) => {
    const result = track(() => updateAction.value);
    if (isSuccessfulAction(result)) {
      showEditForm.value = false;
      await showToast$({
        message: "Изменения встречи сохранены",
        tone: "success",
      });
    }
  });

  useTask$(async ({ track }) => {
    const result = track(() => cancelAction.value);
    if (isSuccessfulAction(result)) {
      showCancelDialog.value = false;
      await showToast$({ message: "Встреча отменена", tone: "info" });
    }
  });

  useTask$(async ({ track }) => {
    const result = track(() => revokeInviteAction.value);
    if (isSuccessfulAction(result)) {
      showRevokeInviteDialog.value = false;
      await showToast$({
        message: "Ссылка удалена из активного списка",
        tone: "warning",
      });
    }
  });

  return (
    <div class="space-y-6">
      <MeetingsOverview
        rooms={roomsData.value.content}
        meetings={meetingsData.value.content}
        totalMeetings={meetingsData.value.totalElements}
        selectedRoomId={selectedRoomId}
        editingMeeting={editingMeeting}
        showCreateForm={showCreateForm}
        showEditForm={showEditForm}
        createAction={createAction}
        updateAction={updateAction}
        createRunning={createAction.isRunning}
        updateRunning={updateAction.isRunning}
        createError={createError}
        updateError={updateError}
        createValidationFeedback={createValidationFeedback}
        updateValidationFeedback={updateValidationFeedback}
        onRoomChange$={selectRoom$}
        onEdit$={openEdit$}
        onCancel$={openCancel$}
        onParticipants$={openParticipants$}
        onInvites$={openInvites$}
        onCreate$={openCreate$}
      />

      {selectedMeeting && (
        <ParticipantPanel
          meeting={selectedMeeting}
          currentUserId={authStore.profile?.id ?? ""}
          currentUserDisplayName={
            authStore.profile?.displayName ?? "Текущий пользователь"
          }
          participants={participantsData.value}
          assignableUsers={assignableUsersData.value}
          bulkAssignAction={bulkAssignAction}
          updateRoleAction={updateRoleAction}
          unassignAction={unassignAction}
          error={participantError}
          onClose$={closeDetail$}
        />
      )}

      {selectedInviteMeeting && (
        <MeetingInvitesWorkspace
          meeting={selectedInviteMeeting}
          invites={invitesData.value.content}
          totalInvites={invitesData.value.totalElements}
          showCreateForm={showInviteForm.value}
          createAction={createInviteAction}
          createRunning={createInviteAction.isRunning}
          error={inviteError}
          onClose$={closeDetail$}
          onCopyLink$={copyInviteLink$}
          onRevoke$={openRevokeInvite$}
          onCreate$={openInviteCreate$}
          onCancelCreate$={closeInviteCreate$}
        />
      )}

      <MeetingConfirmationDialogs
        confirmingCancel={confirmingCancel}
        confirmingRevokeInvite={confirmingRevokeInvite}
        selectedInviteMeeting={selectedInviteMeeting}
        showCancelDialog={showCancelDialog}
        showRevokeInviteDialog={showRevokeInviteDialog}
        cancelAction={cancelAction}
        revokeInviteAction={revokeInviteAction}
        cancelRunning={cancelAction.isRunning}
        revokeRunning={revokeInviteAction.isRunning}
        cancelError={cancelError}
        inviteError={inviteError}
      />

      <AppToast toast={toast.value} onDismiss$={clearToast$} />
    </div>
  );
});
