/* eslint-disable @typescript-eslint/ban-ts-comment */
// @ts-nocheck
import { describe, expect, it, vi } from "vitest";
import { noSerialize } from "@qwik.dev/core";
import type { Meeting, ParticipantAssignment } from "~/lib/domains/meetings";
import type { Room } from "~/lib/domains/rooms";
import {
  findNode,
  findNodes,
  eventHandler,
  renderNode,
  textContent,
} from "./support/jsx-tree";

vi.mock("@qwik.dev/core", async (importOriginal) => {
  const actual = await importOriginal();
  return {
    ...actual,
    get _captures() {
      return actual._captures;
    },
    component$:
      <TProps extends object>(render: (props: TProps) => unknown) =>
      (props: TProps) =>
        actual.jsx(render as never, props as never),
    componentQrl: <T>(value: T): T => value,
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
    onEdit$: noSerialize(vi.fn()),
    onCancel$: noSerialize(vi.fn()),
    onParticipants$: noSerialize(vi.fn()),
    onInvites$: noSerialize(vi.fn()),
    onCreate$: noSerialize(vi.fn()),
    onRoomAction$: noSerialize(vi.fn()),
    roomPreviews: {},
    ...overrides,
  };
}

describe("meetings presentation", () => {
  it("nests previews in their own room and creates a meeting in that room", async () => {
    const { MeetingsOverview } =
      await import("~/routes/meetings/components/MeetingsOverview");
    const onCreate = vi.fn();
    const tree = await renderNode(
      MeetingsOverview(
        overviewProps({
          rooms: [
            createRoom(),
            createRoom({ roomId: "room-2", name: "Терапия", status: "closed" }),
          ],
          roomPreviews: {
            "room-1": { content: [createMeeting()], totalElements: 5 },
            "room-2": { content: [], totalElements: 0 },
          },
          onCreate$: noSerialize(onCreate),
        }),
      ),
    );
    const rooms = findNodes(
      tree,
      (node) => node.type === "article" && Boolean(node.props["data-room-id"]),
    );
    expect(rooms).toHaveLength(2);
    expect(textContent(rooms[0])).toContain("Консилиум");
    expect(textContent(rooms[1])).not.toContain("Консилиум");
    const create = findNode(
      rooms[0],
      (node) =>
        node.type === "button" && textContent(node).includes("Создать встречу"),
    );
    // Qwik passes optimized loop items from q:p as the third event argument.
    await eventHandler(create, "click")(
      undefined,
      undefined,
      create.props["q:p"],
    );
    expect(onCreate).toHaveBeenCalledWith("room-1");
    expect(
      findNode(
        rooms[1],
        (node) =>
          node.type === "button" &&
          textContent(node).includes("Создать встречу"),
      ),
    ).toBeUndefined();
    expect(
      findNode(
        rooms[0],
        (node) =>
          node.type === "a" && node.props.href.includes("meetingId=meeting-1"),
      ),
    ).toBeDefined();
  });

  it("does not describe a failed preview as an empty room", async () => {
    const { MeetingsOverview } =
      await import("~/routes/meetings/components/MeetingsOverview");
    const tree = await renderNode(
      MeetingsOverview(
        overviewProps({
          rooms: [createRoom()],
          roomPreviews: { "room-1": { error: true } },
        }),
      ),
    );
    expect(textContent(tree)).toContain("Не удалось загрузить встречи");
    expect(textContent(tree)).not.toContain("В этой комнате пока нет встреч");
  });

  it("creates the first room without leaving the workspace", async () => {
    const { MeetingsOverview } =
      await import("~/routes/meetings/components/MeetingsOverview");
    const tree = await renderNode(MeetingsOverview(overviewProps()));
    const content = textContent(tree);
    const roomLink = findNode(
      tree,
      (node) =>
        node.type === "button" && textContent(node).includes("Создать комнату"),
    );

    expect(content).toContain("Начните с комнаты");
    expect(content).toContain("Создать комнату");
    expect(content).not.toContain("Выберите комнату");
    expect(roomLink).toBeDefined();
  });

  it("keeps room context visible and renders the schedule once selected", async () => {
    const { MeetingsOverview } =
      await import("~/routes/meetings/components/MeetingsOverview");
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
    expect(findNode(tree, (node) => node.type === "select")).toBeUndefined();
  });

  it("offers one explicit action instead of duplicate controls for a single room", async () => {
    const { MeetingsOverview } =
      await import("~/routes/meetings/components/MeetingsOverview");
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
        node.props.href === "/meetings?roomId=room-1#room-room-1",
    );

    expect(content).toContain("Кардиология");
    expect(content).toContain("Открыть расписание");
    expect(openScheduleLink).toBeDefined();
    expect(findNode(tree, (node) => node.type === "select")).toBeUndefined();
  });

  it("submits only selected participant ids and uses localized roles", async () => {
    const { ParticipantDirectory } =
      await import("~/lib/domains/meetings/components/ParticipantDirectory");
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
    const { ParticipantSelfAssignment } =
      await import("~/lib/domains/meetings/components/ParticipantSelfAssignment");
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
    const { ParticipantCurrentList } =
      await import("~/lib/domains/meetings/components/ParticipantCurrentList");
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
        onDeleteConfirm$: noSerialize(vi.fn()),
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
