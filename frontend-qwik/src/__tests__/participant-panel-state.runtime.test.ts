import { describe, expect, it } from "vitest";
import type {
  ParticipantAssignment,
  UserProfileSummary,
} from "~/lib/domains/meetings";
import {
  buildParticipantDirectoryState,
  buildParticipantFiltersHref,
  normalizeParticipantSortMode,
  resetParticipantFiltersHref,
  toggleSelectedParticipant,
} from "~/lib/domains/meetings/components/participant-panel-state";

function createUser(
  subjectId: string,
  fullName: string,
  organization: string,
): UserProfileSummary {
  return {
    subjectId,
    fullName,
    organization,
    position: "Врач",
  };
}

function createAssignment(subjectId: string): ParticipantAssignment {
  return {
    assignmentId: `assignment-${subjectId}`,
    meetingId: "meeting-1",
    subjectId,
    role: "participant",
    assignedBy: "dev-admin",
    assignedAt: "2026-07-29T10:00:00Z",
    createdAt: "2026-07-29T10:00:00Z",
    updatedAt: "2026-07-29T10:00:00Z",
    fullName: null,
    organization: null,
    position: null,
  };
}

describe("participant panel state", () => {
  it("normalizes unsupported sort modes", () => {
    expect(normalizeParticipantSortMode("organization")).toBe("organization");
    expect(normalizeParticipantSortMode("fullName")).toBe("fullName");
    expect(normalizeParticipantSortMode("unexpected")).toBe("fullName");
    expect(normalizeParticipantSortMode(null)).toBe("fullName");
  });

  it("builds a sorted directory without mutating loader data", () => {
    const users = [
      createUser("u-2", "Яковлев Ян", "Больница Б"),
      createUser("u-1", "Алексеева Анна", "Больница А"),
      createUser("u-3", "Борисов Борис", "Больница А"),
    ];
    const originalOrder = users.map((user) => user.subjectId);

    const state = buildParticipantDirectoryState(
      [createAssignment("u-1")],
      users,
      ["u-2", "stale-user"],
      "organization",
    );

    expect(users.map((user) => user.subjectId)).toEqual(originalOrder);
    expect(state.organizations).toEqual(["Больница А", "Больница Б"]);
    expect(state.sortedUsers.map((user) => user.subjectId)).toEqual([
      "u-1",
      "u-3",
      "u-2",
    ]);
    expect(state.selectableUsers.map((user) => user.subjectId)).toEqual([
      "u-3",
      "u-2",
    ]);
    expect(state.selectedIds).toEqual(["u-2"]);
    expect(state.allVisibleSelected).toBe(false);
  });

  it("toggles selection idempotently", () => {
    expect(toggleSelectedParticipant(["u-1"], "u-1", true)).toEqual(["u-1"]);
    expect(toggleSelectedParticipant(["u-1"], "u-2", true)).toEqual([
      "u-1",
      "u-2",
    ]);
    expect(toggleSelectedParticipant(["u-1", "u-2"], "u-1", false)).toEqual([
      "u-2",
    ]);
  });

  it("updates and clears participant filters while preserving meeting context", () => {
    const current =
      "roomId=room-1&meetingId=meeting-1&participantQuery=old&participantOrganization=old";

    expect(
      buildParticipantFiltersHref("/meetings/", current, {
        query: "  Иванов  ",
        organization: " ЦРБ ",
        sort: "organization",
      }),
    ).toBe(
      "/meetings/?roomId=room-1&meetingId=meeting-1&participantQuery=%D0%98%D0%B2%D0%B0%D0%BD%D0%BE%D0%B2&participantOrganization=%D0%A6%D0%A0%D0%91&participantSort=organization",
    );

    expect(resetParticipantFiltersHref("/meetings/", current)).toBe(
      "/meetings/?roomId=room-1&meetingId=meeting-1",
    );
  });
});
