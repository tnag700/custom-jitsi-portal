import { $, component$, useSignal, useTask$ } from "@qwik.dev/core";
import { Form, Link, routeAction$, routeLoader$, z, zod$, useLocation, useNavigate } from "@qwik.dev/router";
import type { SafeUserProfile } from "~/lib/domains/auth";
import { ApiErrorAlert } from "~/lib/shared";
import { fetchRooms } from "~/lib/domains/rooms";
import {
  MeetingForm,
  MeetingList,
  MeetingServiceError,
  ParticipantPanel,
  assignParticipant,
  assignParticipantSchema,
  cancelMeeting,
  createMeeting,
  createMeetingSchema,
  fetchMeetings,
  fetchParticipants,
  unassignParticipant,
  updateMeeting,
  updateMeetingSchema,
  updateParticipantRole,
  updateParticipantRoleSchema,
  type Meeting,
  type MeetingErrorPayload,
  type PagedMeetingResponse,
} from "~/lib/domains/meetings";
import {
  InviteForm,
  InviteList,
  InviteServiceError,
  createInvite,
  createInviteSchema,
  fetchInvites,
  revokeInvite,
  type Invite,
  type InviteErrorPayload,
  type PagedInviteResponse,
} from "~/lib/domains/invites";

const emptyMeetingsPage: PagedMeetingResponse = {
  content: [],
  page: 0,
  pageSize: 20,
  totalElements: 0,
  totalPages: 0,
};

const emptyInvitesPage: PagedInviteResponse = {
  content: [],
  page: 0,
  pageSize: 20,
  totalElements: 0,
  totalPages: 0,
};

export const useActiveRooms = routeLoader$(async ({ sharedMap, cookie }) => {
  const user = sharedMap.get("user") as SafeUserProfile;
  const apiUrl = sharedMap.get("apiUrl") as string;
  const sessionCookie = cookie.get("JSESSIONID")?.value ?? "";
  try {
    const rooms = await fetchRooms(sessionCookie, apiUrl, user.tenant);
    return {
      ...rooms,
      content: rooms.content.filter((room) => room.status === "active"),
    };
  } catch {
    return { content: [], page: 0, pageSize: 20, totalElements: 0, totalPages: 0 };
  }
});

export const useMeetings = routeLoader$(async ({ sharedMap, cookie, query }) => {
  const apiUrl = sharedMap.get("apiUrl") as string;
  const sessionCookie = cookie.get("JSESSIONID")?.value ?? "";
  const roomId = query.get("roomId");

  if (!roomId) {
    return emptyMeetingsPage;
  }

  try {
    return await fetchMeetings(sessionCookie, apiUrl, roomId);
  } catch {
    return emptyMeetingsPage;
  }
});

export const useParticipants = routeLoader$(async ({ sharedMap, cookie, query }) => {
  const apiUrl = sharedMap.get("apiUrl") as string;
  const sessionCookie = cookie.get("JSESSIONID")?.value ?? "";
  const roomId = query.get("roomId");
  const meetingId = query.get("meetingId");
  const uuidLike =
    /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

  if (!roomId || !meetingId || !uuidLike.test(meetingId)) {
    return [];
  }

  try {
    return await fetchParticipants(sessionCookie, apiUrl, meetingId);
  } catch {
    return [];
  }
});

export const useInvites = routeLoader$(async ({ sharedMap, cookie, query }) => {
  const apiUrl = sharedMap.get("apiUrl") as string;
  const sessionCookie = cookie.get("JSESSIONID")?.value ?? "";
  const meetingId = query.get("invitesMeetingId");

  if (!meetingId) {
    return emptyInvitesPage;
  }

  try {
    return await fetchInvites(sessionCookie, apiUrl, meetingId);
  } catch {
    return emptyInvitesPage;
  }
});

export const useCreateInvite = routeAction$(
  async (data, { sharedMap, cookie, fail }) => {
    const apiUrl = sharedMap.get("apiUrl") as string;
    const sessionCookie = cookie.get("JSESSIONID")?.value ?? "";
    const csrfToken = cookie.get("XSRF-TOKEN")?.value ?? "";
    const idempotencyKey = crypto.randomUUID();

    try {
      const invite = await createInvite(sessionCookie, apiUrl, csrfToken, idempotencyKey, data.meetingId, {
        role: data.role,
        maxUses: data.maxUses,
        expiresInHours: data.expiresInHours,
      });
      return { success: true as const, invite };
    } catch (error) {
      if (error instanceof InviteServiceError) {
        return fail(400, { error: error.payload });
      }
      return fail(500, { error: { title: "Ошибка", detail: "Неизвестная ошибка", errorCode: "INVITE_UNKNOWN" } });
    }
  },
  zod$(createInviteSchema.extend({ meetingId: z.string().min(1, "meetingId обязателен") })),
);

export const useRevokeInvite = routeAction$(
  async (data, { sharedMap, cookie, fail }) => {
    const apiUrl = sharedMap.get("apiUrl") as string;
    const sessionCookie = cookie.get("JSESSIONID")?.value ?? "";
    const csrfToken = cookie.get("XSRF-TOKEN")?.value ?? "";

    try {
      await revokeInvite(sessionCookie, apiUrl, csrfToken, data.meetingId, data.inviteId);
      return { success: true as const };
    } catch (error) {
      if (error instanceof InviteServiceError) {
        return fail(400, { error: error.payload });
      }
      return fail(500, { error: { title: "Ошибка", detail: "Неизвестная ошибка", errorCode: "INVITE_UNKNOWN" } });
    }
  },
  zod$(z.object({ meetingId: z.string().min(1), inviteId: z.string().min(1) })),
);

export const useCreateMeeting = routeAction$(
  async (data, { sharedMap, cookie, fail }) => {
    const apiUrl = sharedMap.get("apiUrl") as string;
    const sessionCookie = cookie.get("JSESSIONID")?.value ?? "";
    const csrfToken = cookie.get("XSRF-TOKEN")?.value ?? "";
    const idempotencyKey = crypto.randomUUID();

    try {
      const meeting = await createMeeting(sessionCookie, apiUrl, csrfToken, idempotencyKey, data.roomId, {
        title: data.title,
        description: data.description,
        meetingType: data.meetingType,
        startsAt: data.startsAt,
        endsAt: data.endsAt,
        allowGuests: data.allowGuests,
        recordingEnabled: data.recordingEnabled,
      });
      return { success: true as const, meeting };
    } catch (error) {
      if (error instanceof MeetingServiceError) {
        return fail(400, { error: error.payload });
      }
      return fail(500, { error: { title: "Ошибка", detail: "Неизвестная ошибка", errorCode: "MEETING_UNKNOWN" } });
    }
  },
  zod$(createMeetingSchema.and(z.object({ roomId: z.string().min(1, "roomId обязателен") }))),
);

export const useUpdateMeeting = routeAction$(
  async (data, { sharedMap, cookie, fail }) => {
    const apiUrl = sharedMap.get("apiUrl") as string;
    const sessionCookie = cookie.get("JSESSIONID")?.value ?? "";
    const csrfToken = cookie.get("XSRF-TOKEN")?.value ?? "";

    try {
      const meeting = await updateMeeting(sessionCookie, apiUrl, csrfToken, data.meetingId, {
        title: data.title,
        description: data.description,
        meetingType: data.meetingType,
        startsAt: data.startsAt,
        endsAt: data.endsAt,
        allowGuests: data.allowGuests,
        recordingEnabled: data.recordingEnabled,
      });
      return { success: true as const, meeting };
    } catch (error) {
      if (error instanceof MeetingServiceError) {
        return fail(400, { error: error.payload });
      }
      return fail(500, { error: { title: "Ошибка", detail: "Неизвестная ошибка", errorCode: "MEETING_UNKNOWN" } });
    }
  },
  zod$(updateMeetingSchema.and(z.object({ meetingId: z.string().min(1, "meetingId обязателен") }))),
);

export const useCancelMeeting = routeAction$(
  async (data, { sharedMap, cookie, fail }) => {
    const apiUrl = sharedMap.get("apiUrl") as string;
    const sessionCookie = cookie.get("JSESSIONID")?.value ?? "";
    const csrfToken = cookie.get("XSRF-TOKEN")?.value ?? "";

    try {
      const meeting = await cancelMeeting(sessionCookie, apiUrl, csrfToken, data.meetingId);
      return { success: true as const, meeting };
    } catch (error) {
      if (error instanceof MeetingServiceError) {
        return fail(400, { error: error.payload });
      }
      return fail(500, { error: { title: "Ошибка", detail: "Неизвестная ошибка", errorCode: "MEETING_UNKNOWN" } });
    }
  },
  zod$(z.object({ meetingId: z.string().min(1, "meetingId обязателен") })),
);

export const useAssignParticipant = routeAction$(
  async (data, { sharedMap, cookie, fail }) => {
    const apiUrl = sharedMap.get("apiUrl") as string;
    const sessionCookie = cookie.get("JSESSIONID")?.value ?? "";
    const csrfToken = cookie.get("XSRF-TOKEN")?.value ?? "";
    const idempotencyKey = crypto.randomUUID();

    try {
      const assignment = await assignParticipant(
        sessionCookie,
        apiUrl,
        csrfToken,
        idempotencyKey,
        data.meetingId,
        { subjectId: data.subjectId, role: data.role },
      );
      return { success: true as const, assignment };
    } catch (error) {
      if (error instanceof MeetingServiceError) {
        return fail(400, { error: error.payload });
      }
      return fail(500, { error: { title: "Ошибка", detail: "Неизвестная ошибка", errorCode: "MEETING_UNKNOWN" } });
    }
  },
  zod$(assignParticipantSchema.extend({ meetingId: z.string().min(1, "meetingId обязателен") })),
);

export const useUpdateParticipantRole = routeAction$(
  async (data, { sharedMap, cookie, fail }) => {
    const apiUrl = sharedMap.get("apiUrl") as string;
    const sessionCookie = cookie.get("JSESSIONID")?.value ?? "";
    const csrfToken = cookie.get("XSRF-TOKEN")?.value ?? "";

    try {
      const assignment = await updateParticipantRole(sessionCookie, apiUrl, csrfToken, data.meetingId, data.subjectId, {
        role: data.role,
      });
      return { success: true as const, assignment };
    } catch (error) {
      if (error instanceof MeetingServiceError) {
        return fail(400, { error: error.payload });
      }
      return fail(500, { error: { title: "Ошибка", detail: "Неизвестная ошибка", errorCode: "MEETING_UNKNOWN" } });
    }
  },
  zod$(
    updateParticipantRoleSchema.extend({
      meetingId: z.string().min(1, "meetingId обязателен"),
      subjectId: z.string().min(1, "subjectId обязателен"),
    }),
  ),
);

export const useUnassignParticipant = routeAction$(
  async (data, { sharedMap, cookie, fail }) => {
    const apiUrl = sharedMap.get("apiUrl") as string;
    const sessionCookie = cookie.get("JSESSIONID")?.value ?? "";
    const csrfToken = cookie.get("XSRF-TOKEN")?.value ?? "";

    try {
      await unassignParticipant(sessionCookie, apiUrl, csrfToken, data.meetingId, data.subjectId);
      return { success: true as const };
    } catch (error) {
      if (error instanceof MeetingServiceError) {
        return fail(400, { error: error.payload });
      }
      return fail(500, { error: { title: "Ошибка", detail: "Неизвестная ошибка", errorCode: "MEETING_UNKNOWN" } });
    }
  },
  zod$(
    z.object({
      meetingId: z.string().min(1, "meetingId обязателен"),
      subjectId: z.string().min(1, "subjectId обязателен"),
    }),
  ),
);

export default component$(() => {
  const nav = useNavigate();
  const loc = useLocation();

  const roomsData = useActiveRooms();
  const meetingsData = useMeetings();
  const participantsData = useParticipants();
  const invitesData = useInvites();

  const createAction = useCreateMeeting();
  const updateAction = useUpdateMeeting();
  const cancelAction = useCancelMeeting();
  const assignAction = useAssignParticipant();
  const updateRoleAction = useUpdateParticipantRole();
  const unassignAction = useUnassignParticipant();
  const createInviteAction = useCreateInvite();
  const revokeInviteAction = useRevokeInvite();

  const showCreateForm = useSignal(false);
  const editingMeeting = useSignal<Meeting | null>(null);
  const confirmingCancel = useSignal<Meeting | null>(null);
  const showInviteForm = useSignal(false);
  const confirmingRevokeInvite = useSignal<Invite | null>(null);
  const toastMessage = useSignal("");

  const selectedRoomId = loc.url.searchParams.get("roomId") ?? "";
  const selectedMeetingId = loc.url.searchParams.get("meetingId") ?? "";
  const selectedInviteMeetingId = loc.url.searchParams.get("invitesMeetingId") ?? "";

  const selectedMeeting =
    meetingsData.value.content.find((meeting) => meeting.meetingId === selectedMeetingId) ?? null;

  const selectedInviteMeeting =
    meetingsData.value.content.find((meeting) => meeting.meetingId === selectedInviteMeetingId) ?? null;

  const createError: MeetingErrorPayload | undefined =
    createAction.value && "error" in createAction.value
      ? (createAction.value as { error: MeetingErrorPayload }).error
      : undefined;

  const updateError: MeetingErrorPayload | undefined =
    updateAction.value && "error" in updateAction.value
      ? (updateAction.value as { error: MeetingErrorPayload }).error
      : undefined;

  const cancelError: MeetingErrorPayload | undefined =
    cancelAction.value && "error" in cancelAction.value
      ? (cancelAction.value as { error: MeetingErrorPayload }).error
      : undefined;

  const participantError: MeetingErrorPayload | undefined =
    (assignAction.value && "error" in assignAction.value
      ? (assignAction.value as { error: MeetingErrorPayload }).error
      : undefined) ??
    (updateRoleAction.value && "error" in updateRoleAction.value
      ? (updateRoleAction.value as { error: MeetingErrorPayload }).error
      : undefined) ??
    (unassignAction.value && "error" in unassignAction.value
      ? (unassignAction.value as { error: MeetingErrorPayload }).error
      : undefined);

  const inviteError: InviteErrorPayload | undefined =
    (createInviteAction.value && "error" in createInviteAction.value
      ? (createInviteAction.value as { error: InviteErrorPayload }).error
      : undefined) ??
    (revokeInviteAction.value && "error" in revokeInviteAction.value
      ? (revokeInviteAction.value as { error: InviteErrorPayload }).error
      : undefined);

  const navigateWithRoom$ = $((roomId: string) => {
    void nav(`/meetings?roomId=${encodeURIComponent(roomId)}`);
  });

  const handleParticipants$ = $((meeting: Meeting) => {
    void nav(`/meetings?roomId=${encodeURIComponent(meeting.roomId)}&meetingId=${encodeURIComponent(meeting.meetingId)}`);
  });

  const closeParticipants$ = $(() => {
    if (selectedRoomId) {
      void nav(`/meetings?roomId=${encodeURIComponent(selectedRoomId)}`);
      return;
    }
    void nav("/meetings");
  });

  const handleInvites$ = $((meeting: Meeting) => {
    void nav(
      `/meetings?roomId=${encodeURIComponent(meeting.roomId)}&invitesMeetingId=${encodeURIComponent(meeting.meetingId)}`,
    );
  });

  const closeInvites$ = $(() => {
    if (selectedRoomId) {
      void nav(`/meetings?roomId=${encodeURIComponent(selectedRoomId)}`);
      return;
    }
    void nav("/meetings");
  });

  const copyInviteLink$ = $((invite: Invite) => {
    if (typeof window === "undefined") return;
    const url = `${window.location.origin}/invite/${invite.token}/`;
    void navigator.clipboard.writeText(url);
    toastMessage.value = "Ссылка скопирована";
    setTimeout(() => {
      toastMessage.value = "";
    }, 3000);
  });

  useTask$(({ track }) => {
    const createInviteResult = track(() => createInviteAction.value);
    if (!createInviteResult || !("success" in createInviteResult) || !createInviteResult.success) {
      return;
    }

    showInviteForm.value = false;
    toastMessage.value = "Инвайт создан";
    setTimeout(() => {
      toastMessage.value = "";
    }, 3000);
  });

  return (
    <div class="space-y-6">
      <section class="rounded border border-border bg-surface p-4">
        <h2 class="mb-3 text-sm font-semibold text-text">Выберите комнату</h2>

        {roomsData.value.content.length === 0 ? (
          <p class="text-sm text-muted">Нет активных комнат</p>
        ) : (
          <>
            <label class="sr-only" for="room-selector">Комната</label>
            <select
              id="room-selector"
              class="w-full rounded border border-border bg-bg px-3 py-2 text-text focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500"
              value={selectedRoomId}
              onChange$={(_, el) => navigateWithRoom$(el.value)}
            >
              <option value="">-- Выберите комнату --</option>
              {roomsData.value.content.map((room) => (
                <option key={room.roomId} value={room.roomId}>
                  {room.name}
                </option>
              ))}
            </select>

            <div class="mt-3 flex flex-wrap gap-2">
              {roomsData.value.content.map((room) => (
                <Link
                  key={room.roomId}
                  href={`/meetings?roomId=${encodeURIComponent(room.roomId)}`}
                  class={[
                    "rounded border px-2 py-1 text-xs",
                    selectedRoomId === room.roomId
                      ? "border-blue-500 bg-blue-50 text-blue-700 dark:bg-blue-950 dark:text-blue-200"
                      : "border-border text-muted hover:bg-bg",
                  ]}
                >
                  {room.name}
                </Link>
              ))}
            </div>
          </>
        )}
      </section>

      {selectedRoomId ? (
        <MeetingList
          meetings={meetingsData.value.content}
          totalElements={meetingsData.value.totalElements}
          onEdit$={$((meeting: Meeting) => {
            editingMeeting.value = meeting;
          })}
          onCancel$={$((meeting: Meeting) => {
            confirmingCancel.value = meeting;
          })}
          onParticipants$={handleParticipants$}
          onInvites$={handleInvites$}
          onCreateClick$={$(() => {
            showCreateForm.value = true;
          })}
        />
      ) : (
        <div class="rounded border border-dashed border-border p-8 text-center text-sm text-muted">
          Выберите комнату, чтобы загрузить встречи
        </div>
      )}

      {showCreateForm.value && selectedRoomId && (
        <MeetingForm
          action={createAction}
          roomId={selectedRoomId}
          isLoading={createAction.isRunning}
          error={createError}
          onCancel$={$(() => {
            showCreateForm.value = false;
          })}
        />
      )}

      {editingMeeting.value && (
        <MeetingForm
          action={updateAction}
          roomId={editingMeeting.value.roomId}
          meeting={editingMeeting.value}
          isLoading={updateAction.isRunning}
          error={updateError}
          onCancel$={$(() => {
            editingMeeting.value = null;
          })}
        />
      )}

      {confirmingCancel.value && (
        <div class="fixed inset-0 z-50 flex items-center justify-center bg-black/50">
          <div class="w-full max-w-sm rounded border border-border bg-surface p-6">
            <h2 class="mb-2 text-lg font-semibold text-text">Отменить встречу?</h2>
            <p class="mb-4 text-sm text-muted">Встреча будет переведена в статус canceled.</p>
            {cancelError && (
              <div class="mb-3" role="alert">
                <ApiErrorAlert
                  title="Ошибка отмены встречи"
                  message={
                    cancelError.errorCode === "MEETING_FINALIZED"
                      ? "Встреча уже завершена или отменена"
                      : cancelError.errorCode === "INVALID_SCHEDULE"
                        ? "Некорректное расписание"
                        : cancelError.detail
                  }
                  errorCode={cancelError.errorCode}
                  traceId={cancelError.traceId}
                />
              </div>
            )}
            <div class="flex justify-end gap-3">
              <button
                type="button"
                class="rounded border border-border px-4 py-2 text-sm text-text hover:bg-bg focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500"
                onClick$={() => {
                  confirmingCancel.value = null;
                }}
              >
                Отмена
              </button>
              <Form action={cancelAction}>
                <input type="hidden" name="meetingId" value={confirmingCancel.value.meetingId} />
                <button
                  type="submit"
                  class="rounded bg-amber-600 px-4 py-2 text-sm text-white hover:bg-amber-700 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-amber-500"
                  disabled={cancelAction.isRunning}
                >
                  {cancelAction.isRunning ? "Отмена..." : "Подтвердить"}
                </button>
              </Form>
            </div>
          </div>
        </div>
      )}

      {selectedMeeting && (
        <ParticipantPanel
          meeting={selectedMeeting}
          participants={participantsData.value}
          assignAction={assignAction}
          updateRoleAction={updateRoleAction}
          unassignAction={unassignAction}
          error={participantError}
          onClose$={closeParticipants$}
        />
      )}

      {selectedInviteMeeting && (
        <div class="rounded border border-border bg-surface p-4">
          <div class="mb-3 flex items-center justify-between gap-2">
            <p class="text-sm text-muted">
              Управление инвайтами для встречи:{" "}
              <span class="font-semibold text-text">{selectedInviteMeeting.title}</span>
            </p>
            <button
              type="button"
              class="rounded border border-border px-3 py-1 text-sm text-text hover:bg-bg focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500"
              onClick$={closeInvites$}
            >
              Закрыть
            </button>
          </div>

          <InviteList
            invites={invitesData.value.content}
            totalElements={invitesData.value.totalElements}
            onCopyLink$={copyInviteLink$}
            onRevoke$={$((invite: Invite) => {
              confirmingRevokeInvite.value = invite;
            })}
            onCreateClick$={$(() => {
              showInviteForm.value = true;
            })}
          />
        </div>
      )}

      {showInviteForm.value && selectedInviteMeeting && (
        <InviteForm
          meetingId={selectedInviteMeeting.meetingId}
          action={createInviteAction}
          isLoading={createInviteAction.isRunning}
          error={inviteError}
          onCancel$={$(() => {
            showInviteForm.value = false;
          })}
        />
      )}

      {confirmingRevokeInvite.value && selectedInviteMeeting && (
        <div class="fixed inset-0 z-50 flex items-center justify-center bg-black/50">
          <div class="w-full max-w-sm rounded border border-border bg-surface p-6">
            <h2 class="mb-2 text-lg font-semibold text-text">Отозвать инвайт?</h2>
            <p class="mb-4 text-sm text-muted">Инвайт станет недействительным.</p>
            {inviteError && (
              <div class="mb-3" role="alert">
                <ApiErrorAlert
                  title="Ошибка отзыва инвайта"
                  message={
                    inviteError.errorCode === "INVITE_NOT_FOUND"
                      ? "Инвайт не найден"
                      : inviteError.errorCode === "INVITE_ALREADY_REVOKED"
                        ? "Инвайт уже отозван"
                        : inviteError.detail
                  }
                  errorCode={inviteError.errorCode}
                  traceId={inviteError.traceId}
                />
              </div>
            )}
            <div class="flex justify-end gap-3">
              <button
                type="button"
                class="rounded border border-border px-4 py-2 text-sm text-text hover:bg-bg focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500"
                onClick$={() => {
                  confirmingRevokeInvite.value = null;
                }}
              >
                Отмена
              </button>
              <Form action={revokeInviteAction}>
                <input type="hidden" name="meetingId" value={selectedInviteMeeting.meetingId} />
                <input type="hidden" name="inviteId" value={confirmingRevokeInvite.value.id} />
                <button
                  type="submit"
                  class="rounded bg-amber-600 px-4 py-2 text-sm text-white hover:bg-amber-700 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-amber-500"
                  disabled={revokeInviteAction.isRunning}
                >
                  {revokeInviteAction.isRunning ? "Отзываем..." : "Подтвердить"}
                </button>
              </Form>
            </div>
          </div>
        </div>
      )}

      {toastMessage.value && (
        <div
          class="fixed bottom-4 right-4 z-50 rounded bg-green-600 px-4 py-2 text-sm text-white shadow-lg"
          role="status"
        >
          {toastMessage.value}
        </div>
      )}
    </div>
  );
});
