/* eslint-disable @typescript-eslint/ban-ts-comment */
// @ts-nocheck
import { describe, expect, it, vi } from "vitest";

interface RenderedNode {
  type: string;
  props: Record<string, unknown>;
}

interface JsxLikeNode {
  type?: unknown;
  props?: Record<string, unknown>;
}

function asArray<T>(value: T | T[] | null | undefined): T[] {
  if (value == null) {
    return [];
  }
  return Array.isArray(value) ? value : [value];
}

function isJsxLikeNode(value: unknown): value is JsxLikeNode {
  return typeof value === "object" && value !== null && ("type" in value || "props" in value);
}

function renderNode(node: unknown): RenderedNode | unknown {
  if (!isJsxLikeNode(node)) {
    return node;
  }

  const candidate = node;

  if (typeof candidate.type === "function") {
    return renderNode(candidate.type(candidate.props ?? {}));
  }

  if (typeof candidate.type === "string") {
    return { type: candidate.type, props: candidate.props ?? {} };
  }

  return node;
}

function isRenderedNode(value: unknown): value is RenderedNode {
  return typeof value === "object" && value !== null && "type" in value && "props" in value;
}

function renderChildren(node: { props?: Record<string, unknown> }): Array<RenderedNode | string> {
  return asArray(node.props?.children).map((child) => renderNode(child)).filter(isRenderedNode);
}

function collectNodes(node: RenderedNode): RenderedNode[] {
  const children = renderChildren(node).filter(isRenderedNode);
  return [node, ...children.flatMap((child) => collectNodes(child))];
}

function findHeadlessNode(node: RenderedNode, name: string): RenderedNode | undefined {
  return collectNodes(node).find((candidate) => candidate.props["data-headless"] === name);
}

function findHeadlessNodes(node: RenderedNode, name: string): RenderedNode[] {
  return collectNodes(node).filter((candidate) => candidate.props["data-headless"] === name);
}

function textContent(node: unknown): string {
  if (typeof node === "string") {
    return node;
  }

  const rendered = isRenderedNode(node) ? node : renderNode(node);
  if (!isRenderedNode(rendered)) {
    return "";
  }

  return asArray(rendered.props.children).map((child) => textContent(child)).join("");
}

vi.mock("@qwik.dev/core", async (importOriginal) => {
  const actual = await importOriginal();
  return {
    ...actual,
    $: <T>(value: T): T => value,
    inlinedQrl: <T>(value: T): T => value,
    inlinedQrlDEV: <T>(value: T): T => value,
    qrl: <T>(value: T): T => value,
    component$: <TProps extends object>(render: (props: TProps) => unknown) => {
      return (props: TProps) => actual.jsx(render as never, props as never);
    },
    componentQrl: <T>(value: T): T => value,
    Slot: (props: Record<string, unknown>) => actual.jsx("slot", props),
  };
});

vi.mock("@qwik-ui/headless", async () => {
  const actual = await import("@qwik.dev/core");
  const primitive =
    (name: string) =>
    (props: Record<string, unknown>) => actual.jsx("div", { ...props, "data-headless": name });

  return {
    Modal: {
      Root: primitive("Modal.Root"),
      Trigger: primitive("Modal.Trigger"),
      Panel: primitive("Modal.Panel"),
      Title: primitive("Modal.Title"),
      Description: primitive("Modal.Description"),
      Close: primitive("Modal.Close"),
    },
    Popover: {
      Root: primitive("Popover.Root"),
      Trigger: primitive("Popover.Trigger"),
      Panel: primitive("Popover.Panel"),
    },
    Combobox: {
      Root: primitive("Combobox.Root"),
      Label: primitive("Combobox.Label"),
      Control: primitive("Combobox.Control"),
      Input: primitive("Combobox.Input"),
      Trigger: primitive("Combobox.Trigger"),
      Popover: primitive("Combobox.Popover"),
      Item: primitive("Combobox.Item"),
      ItemLabel: primitive("Combobox.ItemLabel"),
      ItemIndicator: primitive("Combobox.ItemIndicator"),
    },
  };
});

describe("shared ui runtime smoke", () => {
  it("AppDialog wires bind:show, trigger labels and semantic slots", async () => {
    vi.spyOn(console, "warn").mockImplementation(() => undefined);

    const { AppDialog } = await import("~/lib/shared/ui/AppDialog");
    const show = { value: true };

    const tree = renderNode(
      AppDialog({
        title: "Диалог",
        description: "Описание",
        "bind:show": show,
      }),
    );

    expect(isRenderedNode(tree)).toBe(true);
    if (!isRenderedNode(tree)) {
      throw new Error("AppDialog should render to a JSX node");
    }

    expect(tree.type).toBe("div");

    const nodes = collectNodes(tree);
    const dialogNode = nodes.find((node) => node.props.role === "dialog");
    const closeNode = nodes.find(
      (node) => node.type === "button" && textContent(node).includes("Закрыть"),
    );

    expect(dialogNode).toBeDefined();
    expect(dialogNode?.props["aria-modal"]).toBe("true");
    expect(textContent(dialogNode)).toContain("Диалог");
    expect(textContent(dialogNode)).toContain("Описание");
    expect(closeNode?.props["data-focus-visible-contract"]).toBe("global-css");
  });

  it("AppToast renders message, title and dismiss button when toast is present", async () => {
    const { AppToast } = await import("~/lib/shared/components/AppToast");
    const dismiss = vi.fn();

    const tree = renderNode(
      AppToast({
        toast: {
          title: "Готово",
          message: "Изменения сохранены",
          tone: "success",
        },
        onDismiss$: dismiss,
      }),
    );

    expect(isRenderedNode(tree)).toBe(true);
    if (!isRenderedNode(tree)) {
      throw new Error("AppToast should render to a JSX node");
    }

    const nodes = collectNodes(tree);
    const buttonNode = nodes.find((node) => node.type === "button");
    expect(textContent(tree)).toContain("Готово");
    expect(textContent(tree)).toContain("Изменения сохранены");
    expect(textContent(buttonNode)).toContain("Закрыть");
  });

  it("AppPopover preserves public props and trigger/panel composition", async () => {
    const { AppPopover } = await import("~/lib/shared/ui/AppPopover");

    const tree = renderNode(AppPopover({ id: "popover-a", floating: "right", gutter: 12 }));

    expect(isRenderedNode(tree)).toBe(true);
    if (!isRenderedNode(tree)) {
      throw new Error("AppPopover should render to a JSX node");
    }

    expect(tree.props["data-headless"]).toBe("Popover.Root");
    expect(tree.props.id).toBe("popover-a");
    expect(tree.props.floating).toBe("right");
    expect(tree.props.gutter).toBe(12);

    const triggerNode = findHeadlessNode(tree, "Popover.Trigger");
    const panelNode = findHeadlessNode(tree, "Popover.Panel");
    expect(triggerNode).toBeDefined();
    expect(panelNode).toBeDefined();
    expect(triggerNode?.props["aria-label"]).toBe("Открыть popover");
    expect(triggerNode?.props["data-focus-visible-contract"]).toBe("global-css");
  });

  it("AppCombobox keeps value bridge, defaults, pass-through root props and item composition", async () => {
    const { AppCombobox, forwardComboboxValue } = await import("~/lib/shared/ui/AppCombobox");
    const handleChange = vi.fn();

    const tree = renderNode(
      AppCombobox({
        items: [
          { value: "one", label: "One" },
          { value: "two", label: "Two" },
        ],
        value: "one",
        label: "Выбор",
        name: "meeting-role",
        loop: false,
        onChange$: handleChange,
      }),
    );

    expect(isRenderedNode(tree)).toBe(true);
    if (!isRenderedNode(tree)) {
      throw new Error("AppCombobox should render to a JSX node");
    }

    expect(tree.props["data-headless"]).toBe("Combobox.Root");
    expect(tree.props.value).toBe("one");
    expect(tree.props.filter).toBe(true);
    expect(tree.props.name).toBe("meeting-role");
    expect(tree.props.loop).toBe(false);
    expect(typeof tree.props.onChange$).toBe("function");
    await forwardComboboxValue("two", handleChange);
    expect(handleChange).toHaveBeenCalledOnce();
    expect(handleChange).toHaveBeenCalledWith("two");

    const labelNode = findHeadlessNode(tree, "Combobox.Label");
    const controlNode = findHeadlessNode(tree, "Combobox.Control");
    const inputNode = findHeadlessNode(tree, "Combobox.Input");
    const triggerNode = findHeadlessNode(tree, "Combobox.Trigger");
    const popoverNode = findHeadlessNode(tree, "Combobox.Popover");
    const itemNodes = findHeadlessNodes(tree, "Combobox.Item");
    const itemLabelNodes = findHeadlessNodes(tree, "Combobox.ItemLabel");

    expect(textContent(labelNode)).toContain("Выбор");
    expect(controlNode).toBeDefined();
    expect(inputNode?.props.placeholder).toBe("Выберите значение");
    expect(inputNode?.props["data-focus-visible-contract"]).toBe("global-css");
    expect(triggerNode?.props["aria-label"]).toBe("Открыть список");
    expect(triggerNode?.props["data-focus-visible-contract"]).toBe("global-css");
    expect(popoverNode).toBeDefined();
    expect(itemNodes).toHaveLength(2);
    expect(itemNodes[0]?.props.value).toBe("one");
    expect(itemNodes[1]?.props.value).toBe("two");
    expect(itemLabelNodes.map((node) => textContent(node))).toEqual(["One", "Two"]);
  });
});