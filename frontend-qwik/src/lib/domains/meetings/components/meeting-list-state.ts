import type { Meeting } from "../types";

export type MeetingStatusFilter = "all" | "scheduled" | "canceled" | "ended";
export type MeetingSortBy = "startsAt" | "title";

export function applyMeetingListState(
  meetings: Meeting[],
  statusFilter: MeetingStatusFilter,
  sortBy: MeetingSortBy,
): Meeting[] {
  const filtered =
    statusFilter === "all"
      ? meetings
      : meetings.filter((meeting) => meeting.status === statusFilter);

  return [...filtered].sort((left, right) => {
    if (sortBy === "title") {
      return left.title.localeCompare(right.title, "ru");
    }

    return new Date(left.startsAt).getTime() - new Date(right.startsAt).getTime();
  });
}
