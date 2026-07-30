import type {
  ParticipantAssignment,
  UserProfileSummary,
} from "../types";

export type ParticipantSortMode = "fullName" | "organization";

export interface ParticipantFilters {
  query: string;
  organization: string;
  sort: ParticipantSortMode;
}

export interface ParticipantDirectoryState {
  assignedSubjectIds: Set<string>;
  organizations: string[];
  sortedUsers: UserProfileSummary[];
  selectableUsers: UserProfileSummary[];
  selectedIds: string[];
  allVisibleSelected: boolean;
}

export function normalizeParticipantSortMode(
  value: string | null,
): ParticipantSortMode {
  return value === "organization" ? "organization" : "fullName";
}

export function toggleSelectedParticipant(
  selectedIds: string[],
  subjectId: string,
  checked: boolean,
): string[] {
  if (checked) {
    return selectedIds.includes(subjectId)
      ? selectedIds
      : [...selectedIds, subjectId];
  }

  return selectedIds.filter((id) => id !== subjectId);
}

export function buildParticipantDirectoryState(
  participants: ParticipantAssignment[],
  assignableUsers: UserProfileSummary[],
  selectedIds: string[],
  sortMode: ParticipantSortMode,
): ParticipantDirectoryState {
  const assignedSubjectIds = new Set(
    participants.map((participant) => participant.subjectId),
  );
  const organizations = [
    ...new Set(
      assignableUsers
        .map((user) => user.organization.trim())
        .filter(Boolean),
    ),
  ].sort((left, right) => left.localeCompare(right, "ru"));
  const sortedUsers = [...assignableUsers].sort((left, right) => {
    if (sortMode === "organization") {
      const byOrganization = left.organization.localeCompare(
        right.organization,
        "ru",
      );
      if (byOrganization !== 0) {
        return byOrganization;
      }
    }

    return left.fullName.localeCompare(right.fullName, "ru");
  });
  const selectableUsers = sortedUsers.filter(
    (user) => !assignedSubjectIds.has(user.subjectId),
  );
  const selectableIds = new Set(
    selectableUsers.map((user) => user.subjectId),
  );
  const activeSelectedIds = selectedIds.filter((subjectId) =>
    selectableIds.has(subjectId),
  );

  return {
    assignedSubjectIds,
    organizations,
    sortedUsers,
    selectableUsers,
    selectedIds: activeSelectedIds,
    allVisibleSelected:
      selectableUsers.length > 0 &&
      selectableUsers.every((user) =>
        activeSelectedIds.includes(user.subjectId),
      ),
  };
}

export function buildParticipantFiltersHref(
  pathname: string,
  currentSearch: string | URLSearchParams,
  filters: ParticipantFilters,
): string {
  const params = new URLSearchParams(currentSearch);
  const query = filters.query.trim();
  const organization = filters.organization.trim();

  if (query) {
    params.set("participantQuery", query);
  } else {
    params.delete("participantQuery");
  }

  if (organization) {
    params.set("participantOrganization", organization);
  } else {
    params.delete("participantOrganization");
  }

  params.set("participantSort", filters.sort);

  const search = params.toString();
  return search ? `${pathname}?${search}` : pathname;
}

export function resetParticipantFiltersHref(
  pathname: string,
  currentSearch: string | URLSearchParams,
): string {
  const params = new URLSearchParams(currentSearch);
  params.delete("participantQuery");
  params.delete("participantOrganization");
  params.delete("participantSort");

  const search = params.toString();
  return search ? `${pathname}?${search}` : pathname;
}
