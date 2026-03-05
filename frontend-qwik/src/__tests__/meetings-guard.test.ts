import { describe, it, expect } from "vitest";
import { existsSync, readFileSync } from "node:fs";
import { join } from "node:path";

const SRC_DIR = join(__dirname, "..");

function readSrc(relativePath: string): string {
  const full = join(SRC_DIR, relativePath);
  if (!existsSync(full)) {
    throw new Error(`File not found: ${relativePath}`);
  }
  return readFileSync(full, "utf-8");
}

describe("Meetings Guard: types (AC: 1, 6)", () => {
  it("types.ts should define Meeting domain interfaces", () => {
    const ts = readSrc("lib/domains/meetings/types.ts");
    expect(ts).toContain("Meeting");
    expect(ts).toContain("PagedMeetingResponse");
    expect(ts).toContain("CreateMeetingRequest");
    expect(ts).toContain("UpdateMeetingRequest");
    expect(ts).toContain("ParticipantAssignment");
    expect(ts).toContain("AssignParticipantRequest");
    expect(ts).toContain("MeetingErrorPayload");
  });
});

describe("Meetings Guard: services (AC: 1, 3-7)", () => {
  it("meetings.service.ts should contain fetchMeetings, createMeeting, updateMeeting, cancelMeeting", () => {
    const ts = readSrc("lib/domains/meetings/meetings.service.ts");
    expect(ts).toContain("fetchMeetings");
    expect(ts).toContain("createMeeting");
    expect(ts).toContain("updateMeeting");
    expect(ts).toContain("cancelMeeting");
  });

  it("participants.service.ts should contain fetchParticipants, assignParticipant, updateParticipantRole, unassignParticipant", () => {
    const ts = readSrc("lib/domains/meetings/participants.service.ts");
    expect(ts).toContain("fetchParticipants");
    expect(ts).toContain("assignParticipant");
    expect(ts).toContain("updateParticipantRole");
    expect(ts).toContain("unassignParticipant");
  });
});

describe("Meetings Guard: zod schemas (AC: 3, 4, 6)", () => {
  it("meetings.zod.ts should contain required schemas", () => {
    const ts = readSrc("lib/domains/meetings/meetings.zod.ts");
    expect(ts).toContain("createMeetingSchema");
    expect(ts).toContain("updateMeetingSchema");
    expect(ts).toContain("assignParticipantSchema");
  });
});

describe("Meetings Guard: components (AC: 1-6)", () => {
  it("MeetingCard.tsx should contain component$", () => {
    const tsx = readSrc("lib/domains/meetings/components/MeetingCard.tsx");
    expect(tsx).toContain("component$");
  });

  it("MeetingForm.tsx should contain component$", () => {
    const tsx = readSrc("lib/domains/meetings/components/MeetingForm.tsx");
    expect(tsx).toContain("component$");
  });

  it("MeetingForm.tsx should use shared ApiErrorAlert for API errors", () => {
    const tsx = readSrc("lib/domains/meetings/components/MeetingForm.tsx");
    expect(tsx).toContain("ApiErrorAlert");
  });

  it("MeetingList.tsx should contain component$", () => {
    const tsx = readSrc("lib/domains/meetings/components/MeetingList.tsx");
    expect(tsx).toContain("component$");
  });

  it("ParticipantPanel.tsx should contain component$", () => {
    const tsx = readSrc("lib/domains/meetings/components/ParticipantPanel.tsx");
    expect(tsx).toContain("component$");
  });

  it("ParticipantPanel.tsx should use shared ApiErrorAlert for API errors", () => {
    const tsx = readSrc("lib/domains/meetings/components/ParticipantPanel.tsx");
    expect(tsx).toContain("ApiErrorAlert");
  });
});

describe("Meetings Guard: barrel export (AC: all)", () => {
  it("index.ts should exist as meetings barrel export", () => {
    const ts = readSrc("lib/domains/meetings/index.ts");
    expect(ts).toBeDefined();
  });
});

describe("Meetings Guard: route (AC: 1-7)", () => {
  it("routes/meetings/index.tsx should contain routeLoader$ and routeAction$", () => {
    const tsx = readSrc("routes/meetings/index.tsx");
    expect(tsx).toContain("routeLoader$");
    expect(tsx).toContain("routeAction$");
  });

  it("routes/meetings/index.tsx should use Form for actions", () => {
    const tsx = readSrc("routes/meetings/index.tsx");
    expect(tsx).toContain("<Form");
  });

  it("routes/meetings/index.tsx should use ApiErrorAlert for cancel/revoke confirmation errors", () => {
    const tsx = readSrc("routes/meetings/index.tsx");
    expect(tsx).toContain("ApiErrorAlert");
    expect(tsx).toContain("Ошибка отмены встречи");
    expect(tsx).toContain("Ошибка отзыва инвайта");
  });
}
);
