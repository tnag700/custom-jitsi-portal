import { beforeEach, describe, expect, it, vi } from "vitest";
import type * as QwikCore from "@qwik.dev/core";
import { RoomForm, type Room, type RoomErrorPayload } from "~/lib/domains/rooms";
import {
  RoomManagement,
  type RoomManagementRequest,
} from "~/routes/rooms/components/RoomManagement";

const actions = vi.hoisted(() => ({
  create: { value: undefined as unknown, isRunning: false },
  update: { value: undefined as unknown, isRunning: false },
  close: { value: undefined as unknown, isRunning: false },
  delete: { value: undefined as unknown, isRunning: false },
}));

const configData = vi.hoisted(() => ({
  value: { configSets: ["cfg-active"], error: undefined as RoomErrorPayload | undefined },
}));

vi.mock("@qwik.dev/core", async (importOriginal) => {
  const actual = await importOriginal<
    typeof QwikCore & { _captures: unknown }
  >();
  const identity = <T>(value: T): T => value;
  const runTask = (
    task: (context: { track: <T>(read: () => T) => T }) => unknown,
  ) => {
    void task({ track: (read) => read() });
  };
  return {
    ...actual,
    component$: identity,
    componentQrl: identity,
    get _captures() {
      return actual._captures;
    },
    useSignal: <T>(value: T) => ({ value }),
    useTask$: runTask,
    useTaskQrl: runTask,
  };
});

vi.mock("~/routes/rooms/route-handlers", () => ({
  useRoomConfigSets: () => configData,
  useCreateRoom: () => actions.create,
  useUpdateRoom: () => actions.update,
  useCloseRoom: () => actions.close,
  useDeleteRoom: () => actions.delete,
}));

const room: Room = {
  roomId: "room-7",
  name: "Консилиум",
  description: "Еженедельные встречи",
  tenantId: "tenant-1",
  configSetId: "cfg-active",
  status: "active",
  createdAt: "2026-09-29T08:00:00Z",
  updatedAt: "2026-09-29T08:00:00Z",
};

interface Node {
  type: unknown;
  props: Record<string, unknown>;
}

// Inspect this component's output without replacing its form/dialog components.
function nodes(value: unknown): Node[] {
  if (Array.isArray(value)) return value.flatMap(nodes);
  if (!value || typeof value !== "object" || !("props" in value)) return [];
  const node = value as Node;
  return [node, ...nodes(node.props.children)];
}

async function render(
  kind: "create" | "edit" | "close" | "delete",
  target: Room | null = room,
) {
  const renderComponent = RoomManagement as unknown as (props: {
    request: { value: RoomManagementRequest };
  }) => unknown;
  return nodes(
    await renderComponent({ request: { value: { kind, room: target } } }),
  );
}

beforeEach(() => {
  configData.value = { configSets: ["cfg-active"], error: undefined };
  for (const action of Object.values(actions)) {
    action.value = undefined;
    action.isRunning = false;
  }
});

describe("shared room management", () => {
  it("shows config load errors on room forms while preserving mutation error priority", async () => {
    configData.value = {
      configSets: [],
      error: { title: "Недоступно", detail: "Повторите позже", errorCode: "CONFIG_SET_NOT_FOUND", traceId: "config-trace-1" },
    };
    const tree = await render("create", null);
    const createForm = tree.find((node) => node.props.action === actions.create)!;
    const updateForm = tree.find((node) => node.props.action === actions.update)!;
    expect(createForm.props.configSets).toEqual([]);
    expect(createForm.props.error).toEqual(configData.value.error);
    expect(updateForm.props.error).toEqual(configData.value.error);

    const actionError = { title: "Ошибка", detail: "Имя занято", errorCode: "ROOM_NAME_CONFLICT" };
    actions.create.value = { error: actionError };
    actions.update.value = { error: actionError };
    const failed = await render("edit");
    expect(failed.find((node) => node.props.action === actions.create)?.props.error).toEqual(actionError);
    expect(failed.find((node) => node.props.action === actions.update)?.props.error).toEqual(actionError);
  });

  it("disables creation without a config while allowing edits with the room's current config", async () => {
    const renderForm = RoomForm as unknown as (props: Record<string, unknown>) => unknown;
    const formProps = { action: actions.create, configSets: [], isLoading: false, isOpen: { value: true } };
    const create = nodes(await renderForm(formProps));
    expect(create.find((node) => node.type === "button" && node.props.type === "submit")?.props.disabled).toBe(true);
    const edit = nodes(await renderForm({ ...formProps, action: actions.update, room }));
    expect(edit.find((node) => node.type === "button" && node.props.type === "submit")?.props.disabled).toBe(false);
    expect(edit.find((node) => node.type === "option")?.props.value).toBe("cfg-active");
  });

  it("opens creation with the active configuration and edits the selected room", async () => {
    const createNodes = await render("create", null);
    const createForm = createNodes.find(
      (node) => node.props.action === actions.create,
    )!;
    expect(createForm.props.configSets).toEqual(["cfg-active"]);
    expect(createForm.props.isOpen).toEqual({ value: true });
    expect(createForm.props.room).toBeUndefined();

    const editNodes = await render("edit");
    const editForm = editNodes.find(
      (node) => node.props.action === actions.update,
    )!;
    expect(editForm.props.room).toEqual(room);
    expect(editForm.props.isOpen).toEqual({ value: true });
    expect(
      editNodes.find((node) => node.props.action === actions.create)?.props
        .isOpen,
    ).toEqual({ value: false });
  });

  it.each(["close", "delete"] as const)(
    "requires confirmation for %s with the selected room ID",
    async (kind) => {
      const tree = await render(kind);
      const confirmation = tree.find(
        (node) => (node.props["bind:show"] as { value?: boolean })?.value,
      );
      expect(confirmation?.props.description).toContain("Консилиум");
      const form = tree.find((node) => node.props.action === actions[kind])!;
      expect(
        nodes(form.props.children).find((node) => node.props.name === "roomId")
          ?.props.value,
      ).toBe("room-7");
      expect(
        nodes(form.props.children).find((node) => node.type === "button")?.props
          .disabled,
      ).toBe(false);

      const empty = await render(kind, null);
      expect(
        empty.some(
          (node) => (node.props["bind:show"] as { value?: boolean })?.value,
        ),
      ).toBe(false);
      const emptyForm = empty.find(
        (node) => node.props.action === actions[kind],
      )!;
      expect(
        nodes(emptyForm.props.children).find((node) => node.type === "button")
          ?.props.disabled,
      ).toBe(true);
    },
  );

  it("keeps a failed deletion open with its trace, and closes it only on success", async () => {
    actions.delete.value = {
      error: {
        errorCode: "ROOM_HAS_ACTIVE_MEETINGS",
        detail: "Conflict",
        traceId: "trace-7",
      },
    };
    const failed = await render("delete");
    expect(
      failed.find((node) => node.props.errorCode === "ROOM_HAS_ACTIVE_MEETINGS")
        ?.props,
    ).toMatchObject({
      message: "Нельзя удалить комнату с активными встречами",
      traceId: "trace-7",
    });
    expect(
      failed.find((node) => node.props.title === "Удалить комнату?")?.props[
        "bind:show"
      ],
    ).toEqual({ value: true });

    actions.delete.value = { success: true };
    const succeeded = await render("delete");
    expect(
      succeeded.find((node) => node.props.title === "Удалить комнату?")?.props[
        "bind:show"
      ],
    ).toEqual({ value: false });
    expect(
      succeeded.find((node) => node.props.toast)?.props.toast,
    ).toMatchObject({ message: "Комната удалена", tone: "warning" });
  });
});
