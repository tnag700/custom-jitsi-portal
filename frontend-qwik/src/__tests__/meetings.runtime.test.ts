import { describe, expect, it } from "vitest";
import { updateMeetingSchema } from "../lib/domains/meetings/meetings.zod";
import { applyMeetingListState } from "../lib/domains/meetings/components/meeting-list-state";
import type { Meeting } from "../lib/domains/meetings/types";

function meeting(overrides: Partial<Meeting>): Meeting {
  return {
    meetingId: "00000000-0000-4000-8000-000000000001",
    roomId: "00000000-0000-4000-8000-000000000010",
    title: "A",
    description: null,
    meetingType: "standard",
    configSetId: "cfg-1",
    status: "scheduled",
    startsAt: "2026-03-10T08:00:00Z",
    endsAt: "2026-03-10T09:00:00Z",
    allowGuests: true,
    recordingEnabled: false,
    createdAt: "2026-03-01T00:00:00Z",
    updatedAt: "2026-03-01T00:00:00Z",
    ...overrides,
  };
}

describe("Meetings runtime: update schema", () => {
  it("rejects invalid time range when startsAt and endsAt provided", () => {
    const result = updateMeetingSchema.safeParse({
      startsAt: "2026-03-10T10:00:00Z",
      endsAt: "2026-03-10T09:00:00Z",
    });

    expect(result.success).toBe(false);
  });

  it("allows partial update with only startsAt", () => {
    const result = updateMeetingSchema.safeParse({
      startsAt: "2026-03-10T10:00:00Z",
    });

    expect(result.success).toBe(true);
  });
});

describe("Meetings runtime: list state", () => {
  const data: Meeting[] = [
    meeting({
      meetingId: "00000000-0000-4000-8000-000000000001",
      title: "Webinar B",
      status: "canceled",
      startsAt: "2026-03-11T10:00:00Z",
    }),
    meeting({
      meetingId: "00000000-0000-4000-8000-000000000002",
      title: "Alpha",
      status: "scheduled",
      startsAt: "2026-03-10T08:00:00Z",
    }),
    meeting({
      meetingId: "00000000-0000-4000-8000-000000000003",
      title: "Gamma",
      status: "ended",
      startsAt: "2026-03-12T09:00:00Z",
    }),
  ];

  it("filters by status", () => {
    const result = applyMeetingListState(data, "ended", "startsAt");
    expect(result).toHaveLength(1);
    expect(result[0]?.status).toBe("ended");
  });

  it("sorts by title in locale-aware order", () => {
    const result = applyMeetingListState(data, "all", "title");
    expect(result.map((item) => item.title)).toEqual(["Alpha", "Gamma", "Webinar B"]);
  });

  it("sorts by startsAt ascending", () => {
    const result = applyMeetingListState(data, "all", "startsAt");
    expect(result.map((item) => item.meetingId)).toEqual([
      "00000000-0000-4000-8000-000000000002",
      "00000000-0000-4000-8000-000000000001",
      "00000000-0000-4000-8000-000000000003",
    ]);
  });
});
