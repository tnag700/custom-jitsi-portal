import {
  $,
  component$,
  sync$,
  useSignal,
  useTask$,
  type QRL,
} from "@qwik.dev/core";
import { useLocation, useNavigate } from "@qwik.dev/router";
import { ApiErrorAlert } from "~/lib/shared";
import type {
  Meeting,
  MeetingErrorPayload,
  ParticipantAssignment,
  UserProfileSummary,
} from "../types";
import { ParticipantCurrentList } from "./ParticipantCurrentList";
import { ParticipantDirectory } from "./ParticipantDirectory";
import { ParticipantSelfAssignment } from "./ParticipantSelfAssignment";
import {
  buildParticipantDirectoryState,
  buildParticipantFiltersHref,
  normalizeParticipantSortMode,
  resetParticipantFiltersHref,
} from "./participant-panel-state";

interface ParticipantPanelProps {
  meeting: Meeting;
  currentUserId: string;
  currentUserDisplayName: string;
  participants: ParticipantAssignment[];
  assignableUsers: UserProfileSummary[];
  bulkAssignAction: unknown;
  updateRoleAction: unknown;
  unassignAction: unknown;
  onClose$: QRL<() => void>;
  error?: MeetingErrorPayload;
}

interface BulkAssignActionLike {
  isRunning?: boolean;
  value?: {
    success?: boolean;
  };
}

const ERROR_MESSAGES: Record<string, string> = {
  ASSIGNMENT_NOT_FOUND: "Назначение не найдено",
  MEETING_ROLE_CONFLICT: "Конфликт ролей",
  INVALID_ROLE: "Недопустимая роль",
};

export const ParticipantPanel = component$<ParticipantPanelProps>(
  ({
    meeting,
    currentUserId,
    currentUserDisplayName,
    participants,
    assignableUsers,
    bulkAssignAction,
    updateRoleAction,
    unassignAction,
    onClose$,
    error,
  }) => {
    const location = useLocation();
    const navigate = useNavigate();
    const panelRef = useSignal<HTMLDivElement>();
    const selectedIds = useSignal<string[]>([]);
    const bulkRole =
      useSignal<ParticipantAssignment["role"]>("participant");
    const searchQuery = useSignal(
      location.url.searchParams.get("participantQuery") ?? "",
    );
    const organizationFilter = useSignal(
      location.url.searchParams.get("participantOrganization") ?? "",
    );
    const sortMode = useSignal(
      normalizeParticipantSortMode(
        location.url.searchParams.get("participantSort"),
      ),
    );
    const bulkActionState = bulkAssignAction as BulkAssignActionLike;
    const directoryState = buildParticipantDirectoryState(
      participants,
      assignableUsers,
      selectedIds.value,
      sortMode.value,
    );
    const errorMessage = error
      ? (ERROR_MESSAGES[error.errorCode] ?? error.detail)
      : null;

    useTask$(({ track }) => {
      const isSuccess = track(
        () => bulkActionState.value?.success === true,
      );
      if (isSuccess) {
        selectedIds.value = [];
      }
    });

    useTask$(({ track }) => {
      track(() => panelRef.value);
      if (typeof window !== "undefined" && panelRef.value) {
        queueMicrotask(() => {
          panelRef.value?.focus();
        });
      }
    });

    const requestClose$ = $(() => {
      if (typeof window === "undefined") {
        void onClose$();
        return;
      }

      window.requestAnimationFrame(() => {
        void onClose$();
      });
    });

    const applyFilters$ = $(() => {
      void navigate(
        buildParticipantFiltersHref(
          location.url.pathname,
          location.url.searchParams,
          {
            query: searchQuery.value,
            organization: organizationFilter.value,
            sort: sortMode.value,
          },
        ),
      );
    });

    const resetFilters$ = $(() => {
      searchQuery.value = "";
      organizationFilter.value = "";
      sortMode.value = "fullName";
      void navigate(
        resetParticipantFiltersHref(
          location.url.pathname,
          location.url.searchParams,
        ),
      );
    });

    const confirmDelete$ = sync$((event: Event) => {
      if (
        typeof window !== "undefined" &&
        !window.confirm("Удалить участника из встречи?")
      ) {
        event.preventDefault();
      }
    });

    return (
      <aside
        class="fixed inset-0 z-50 flex justify-end bg-black/45 backdrop-blur-[1px]"
        role="presentation"
        onClick$={(event, overlay) => {
          if (event.target === overlay) {
            void requestClose$();
          }
        }}
        onKeyDown$={(event) => {
          if (event.key === "Escape") {
            void requestClose$();
          }
        }}
      >
        <div
          ref={panelRef}
          role="dialog"
          aria-modal="true"
          aria-labelledby="participant-panel-title"
          tabIndex={-1}
          class="h-full w-full max-w-3xl overflow-y-auto border-l border-border bg-surface px-4 pb-6 sm:px-6"
        >
          <header class="sticky top-0 z-10 -mx-4 mb-5 flex items-start justify-between gap-3 border-b border-border bg-surface/95 px-4 py-4 backdrop-blur sm:-mx-6 sm:px-6">
            <div class="min-w-0">
              <p class="text-xs font-medium uppercase tracking-wide text-blue-600 dark:text-blue-300">
                Состав встречи
              </p>
              <h2
                id="participant-panel-title"
                class="truncate text-xl font-semibold text-text"
              >
                {meeting.title}
              </h2>
            </div>
            <button
              type="button"
              class="shrink-0 rounded-lg border border-border px-3 py-2 text-sm font-medium text-text hover:bg-bg focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500"
              onClick$={requestClose$}
            >
              Закрыть
            </button>
          </header>

          {errorMessage && (
            <div class="mb-5" role="alert">
              <ApiErrorAlert
                title="Ошибка управления участниками"
                message={errorMessage}
                errorCode={error?.errorCode}
                traceId={error?.traceId}
              />
            </div>
          )}

          <div class="space-y-5">
            {currentUserId && (
              <ParticipantSelfAssignment
                meetingId={meeting.meetingId}
                currentUserId={currentUserId}
                currentUserDisplayName={currentUserDisplayName}
                isAssigned={directoryState.assignedSubjectIds.has(
                  currentUserId,
                )}
                bulkAssignAction={bulkAssignAction}
                isAssigning={!!bulkActionState.isRunning}
              />
            )}
            <ParticipantCurrentList
              meetingId={meeting.meetingId}
              currentUserId={currentUserId}
              participants={participants}
              updateRoleAction={updateRoleAction}
              unassignAction={unassignAction}
              onDeleteConfirm$={confirmDelete$}
            />
            <ParticipantDirectory
              meetingId={meeting.meetingId}
              currentUserId={currentUserId}
              users={directoryState.sortedUsers}
              assignedSubjectIds={[
                ...directoryState.assignedSubjectIds,
              ]}
              selectableSubjectIds={directoryState.selectableUsers.map(
                (user) => user.subjectId,
              )}
              organizations={directoryState.organizations}
              selectedIds={selectedIds}
              searchQuery={searchQuery}
              organizationFilter={organizationFilter}
              sortMode={sortMode}
              bulkRole={bulkRole}
              bulkAssignAction={bulkAssignAction}
              isAssigning={!!bulkActionState.isRunning}
              onApplyFilters$={applyFilters$}
              onResetFilters$={resetFilters$}
            />
          </div>
        </div>
      </aside>
    );
  },
);
