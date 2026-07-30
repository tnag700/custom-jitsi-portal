/* eslint-disable @typescript-eslint/ban-ts-comment */
// @ts-nocheck
import { describe, expect, it, vi } from "vitest";
import { noSerialize } from "@qwik.dev/core";
import type { Meeting, ParticipantAssignment } from "~/lib/domains/meetings";
import type { Room } from "~/lib/domains/rooms";
import {
  findNode,
  findNodes,
  renderNode,
  textContent,
} from "./support/jsx-tree";

vi.mock("@qwik.dev/core", async (importOriginal) => {
  const actual = await importOriginal();
  return {
    ...actual,
    component$:
      <TProps extends object>(render: (props: TProps) => unknown) =>
      (props: TProps) =>
        actual.jsx(render as never, props as never),
    componentQrl: <T>(value: T): T => value,
    inlinedQrl: <T>(value: T): T => value,
    inlinedQrlDEV: <T>(value: T): T => value,
    qrl: <T>(value: T): T => value,
  };
});

vi.mock("@qwik.dev/router", async () => {
  const actual = await import("@qwik.dev/core");
  return {
    Form: (props: Record<string, unknown>) =>
      actual.jsx("form", { ...props, action: undefined }),
    Link: (props: Record<string, unknown>) => actual.jsx("a", { ...props }),
  };
});

vi.mock("~/lib/domains/meetings", async () => {
  const actual = await import("@qwik.dev/core");
  return {
    MeetingForm: () => actual.jsx("div", { "data-testid": "meeting-form" }),
    MeetingList: (props: Record<string, unknown>) =>
      actual.jsx("div", {
        "data-testid": "meeting-list",
        children: `meetings:${(props.meetings as unknown[]).length}`,
      }),
  };
});

vi.mock("~/lib/shared", () => ({
  formatDateTime: (value: string) => value,
}));

function createRoom(overrides: Partial<Room> = {}): Room {
  return {
    roomId: "room-1",
    name: "Кардиология",
    description: null,
    tenantId: "tenant-1",
    configSetId: "config-1",
    status: "active",
    createdAt: "2026-07-29T09:00:00Z",
    updatedAt: "2026-07-29T09:00:00Z",
    ...overrides,
  };
}

function createMeeting(): Meeting {
  return {
    meetingId: "meeting-1",
    roomId: "room-1",
    title: "Консилиум",
    description: null,
    meetingType: "standard",
    configSetId: "config-1",
    status: "scheduled",
    startsAt: "2026-07-29T10:00:00Z",
    endsAt: "2026-07-29T11:00:00Z",
    allowGuests: true,
    recordingEnabled: false,
    createdAt: "2026-07-29T09:00:00Z",
    updatedAt: "2026-07-29T09:00:00Z",
  };
}

function overviewProps(overrides: Record<string, unknown> = {}) {
  return {
    rooms: [],
    meetings: [],
    totalMeetings: 0,
    selectedRoomId: "",
    editingMeeting: { value: null },
    showCreateForm: { value: false },
    showEditForm: { value: false },
    createAction: {},
    updateAction: {},
    createRunning: false,
    updateRunning: false,
    onRoomChange$: vi.fn(),
    onEdit$: vi.fn(),
    onCancel$: vi.fn(),
    onParticipants$: vi.fn(),
    onInvites$: vi.fn(),
    onCreate$: vi.fn(),
    ...overrides,
  };
}

describe("meetings presentation", () => {
  it("renders one actionable empty state when there are no active rooms", async () => {
    const { MeetingsOverview } = await import(
      "~/routes/meetings/components/MeetingsOverview"
    );
    const tree = await renderNode(MeetingsOverview(overviewProps()));
    const content = textContent(tree);
    const roomLink = findNode(
      tree,
      (node) => node.type === "a" && node.props.href === "/rooms",
    );

    expect(content).toContain("Пока нет активных комнат");
    expect(content).toContain("Перейти к комнатам");
    expect(content).not.toContain("Выберите комнату");
    expect(roomLink).toBeDefined();
  });

  it("keeps room context visible and renders the schedule once selected", async () => {
    const { MeetingsOverview } = await import(
      "~/routes/meetings/components/MeetingsOverview"
    );
    const tree = await renderNode(
      MeetingsOverview(
        overviewProps({
          rooms: [createRoom()],
          meetings: [createMeeting()],
          totalMeetings: 1,
          selectedRoomId: "room-1",
        }),
      ),
    );
    const content = textContent(tree);
    const meetingList = findNode(
      tree,
      (node) => node.props["data-testid"] === "meeting-list",
    );

    expect(content).toContain("Кардиология");
    expect(content).toContain("meetings:1");
    expect(meetingList).toBeDefined();
    expect(
      findNode(tree, (node) => node.type === "select"),
    ).toBeUndefined();
  });

  it("offers one explicit action instead of duplicate controls for a single room", async () => {
    const { MeetingsOverview } = await import(
      "~/routes/meetings/components/MeetingsOverview"
    );
    const tree = await renderNode(
      MeetingsOverview(
        overviewProps({
          rooms: [createRoom()],
        }),
      ),
    );
    const content = textContent(tree);
    const openScheduleLink = findNode(
      tree,
      (node) =>
        node.type === "a" &&
        node.props.href === "/meetings?roomId=room-1",
    );

    expect(content).toContain("Активная комната");
    expect(content).toContain("Открыть расписание");
    expect(openScheduleLink).toBeDefined();
    expect(
      findNode(tree, (node) => node.type === "select"),
    ).toBeUndefined();
  });

  it("submits only selected participant ids and uses localized roles", async () => {
    const { ParticipantDirectory } = await import(
      "~/lib/domains/meetings/components/ParticipantDirectory"
    );
    const selectedIds = { value: ["u-1"] };
    const tree = await renderNode(
      ParticipantDirectory({
        meetingId: "meeting-1",
        currentUserId: "u-1",
        users: [
          {
            subjectId: "u-1",
            fullName: "Иванов Иван",
            organization: "ЦРБ",
            position: "Врач",
          },
        ],
        assignedSubjectIds: [],
        selectableSubjectIds: ["u-1"],
        organizations: ["ЦРБ"],
        selectedIds,
        searchQuery: { value: "" },
        organizationFilter: { value: "" },
        sortMode: { value: "fullName" },
        bulkRole: { value: "participant" },
        bulkAssignAction: {},
        isAssigning: false,
        onApplyFilters$: noSerialize(vi.fn()),
        onResetFilters$: noSerialize(vi.fn()),
      }),
    );
    const content = textContent(tree);
    const subjectFields = findNodes(
      tree,
      (node) => node.type === "input" && node.props.name === "subjectIds[]",
    );
    const roleOptions = findNodes(
      tree,
      (node) =>
        node.type === "option" &&
        ["host", "moderator", "participant"].includes(
          node.props.value as string,
        ),
    );

    expect(subjectFields.map((field) => field.props.value)).toEqual(["u-1"]);
    expect(
      roleOptions.find((option) => option.props.value === "participant")?.props
        .selected,
    ).toBe(true);
    expect(
      roleOptions.find((option) => option.props.value === "host")?.props
        .selected,
    ).toBe(false);
    expect(content).toContain("Организатор");
    expect(content).toContain("Модератор");
    expect(content).toContain("Участник");
    expect(content).toContain("Вы");
    expect(content).toContain("выбрано 1");
    expect(content).toContain("Добавить (1)");
  });

  it("offers a dedicated self-assignment action without requiring directory selection", async () => {
    const { ParticipantSelfAssignment } = await import(
      "~/lib/domains/meetings/components/ParticipantSelfAssignment"
    );
    const tree = await renderNode(
      ParticipantSelfAssignment({
        meetingId: "meeting-1",
        currentUserId: "u-self",
        currentUserDisplayName: "Development Administrator",
        isAssigned: false,
        bulkAssignAction: {},
        isAssigning: false,
      }),
    );
    const content = textContent(tree);
    const subjectFields = findNodes(
      tree,
      (node) => node.type === "input" && node.props.name === "subjectIds[]",
    );
    const submitButton = findNode(
      tree,
      (node) => node.type === "button" && node.props.type === "submit",
    );

    expect(content).toContain("Вы");
    expect(content).toContain("Development Administrator");
    expect(content).toContain("Добавить себя");
    expect(subjectFields.map((field) => field.props.value)).toEqual(["u-self"]);
    expect(submitButton?.props.disabled).toBe(false);

    const assignedTree = await renderNode(
      ParticipantSelfAssignment({
        meetingId: "meeting-1",
        currentUserId: "u-self",
        currentUserDisplayName: "Development Administrator",
        isAssigned: true,
        bulkAssignAction: {},
        isAssigning: false,
      }),
    );

    expect(textContent(assignedTree)).toContain("Вы уже в составе");
    expect(
      findNode(
        assignedTree,
        (node) => node.type === "button" && node.props.type === "submit",
      ),
    ).toBeUndefined();
  });

  it("shows a participant full name before the technical subject id", async () => {
    const { ParticipantCurrentList } = await import(
      "~/lib/domains/meetings/components/ParticipantCurrentList"
    );
    const participant: ParticipantAssignment = {
      assignmentId: "assignment-1",
      meetingId: "meeting-1",
      subjectId: "subject-technical-id",
      role: "host",
      assignedBy: "dev-admin",
      assignedAt: "2026-07-29T10:00:00Z",
      createdAt: "2026-07-29T10:00:00Z",
      updatedAt: "2026-07-29T10:00:00Z",
      fullName: "Иванов Иван",
      organization: "ЦРБ",
      position: "Врач",
    };
    const tree = await renderNode(
      ParticipantCurrentList({
        meetingId: "meeting-1",
        currentUserId: "subject-technical-id",
        participants: [participant],
        updateRoleAction: {},
        unassignAction: {},
        onDeleteConfirm$: vi.fn(),
      }),
    );
    const content = textContent(tree);

    expect(content.indexOf("Иванов Иван")).toBeLessThan(
      content.indexOf("subject-technical-id"),
    );
    expect(content).toContain("Организатор");
    expect(content).toContain("Вы");
  });
});
