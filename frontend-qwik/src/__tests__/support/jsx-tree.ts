export interface RenderedNode {
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
  return (
    typeof value === "object" &&
    value !== null &&
    ("type" in value || "props" in value)
  );
}

export function isRenderedNode(value: unknown): value is RenderedNode {
  return (
    typeof value === "object" &&
    value !== null &&
    "type" in value &&
    typeof value.type === "string" &&
    "props" in value
  );
}

export async function renderNode(node: unknown): Promise<unknown> {
  node = await node;
  if (Array.isArray(node)) {
    return Promise.all(node.map((child) => renderNode(child)));
  }
  if (!isJsxLikeNode(node)) {
    return node;
  }
  if (typeof node.type === "function") {
    return renderNode(node.type(node.props ?? {}));
  }
  if (typeof node.type === "string") {
    const props = node.props ?? {};
    if (!("children" in props)) {
      return { type: node.type, props };
    }

    const children = await renderNode(props.children);
    return {
      type: node.type,
      props: {
        ...props,
        children,
      },
    };
  }
  return renderNode(node.props?.children);
}

export function collectNodes(node: unknown): RenderedNode[] {
  if (Array.isArray(node)) {
    return node.flatMap((child) => collectNodes(child));
  }
  if (!isRenderedNode(node)) {
    return [];
  }
  return [
    node,
    ...asArray(node.props.children).flatMap((child) => collectNodes(child)),
  ];
}

export function findNode(
  node: unknown,
  predicate: (candidate: RenderedNode) => boolean,
): RenderedNode | undefined {
  return collectNodes(node).find(predicate);
}

export function findNodes(
  node: unknown,
  predicate: (candidate: RenderedNode) => boolean,
): RenderedNode[] {
  return collectNodes(node).filter(predicate);
}

export function eventHandler(
  node: RenderedNode | undefined,
  eventName: string,
): unknown {
  if (!node) {
    return undefined;
  }

  const normalizedEvent = eventName.toLowerCase();
  const legacyProp =
    `on${normalizedEvent[0]?.toUpperCase()}${normalizedEvent.slice(1)}$`;
  return node.props[`q-e:${normalizedEvent}`] ?? node.props[legacyProp];
}

export function textContent(node: unknown): string {
  if (Array.isArray(node)) {
    return node.map((child) => textContent(child)).join("");
  }
  if (typeof node === "string" || typeof node === "number") {
    return String(node);
  }

  if (Array.isArray(node)) {
    return node.map((child) => textContent(child)).join("");
  }
  if (!isRenderedNode(node)) {
    return "";
  }
  return asArray(node.props.children)
    .map((child) => textContent(child))
    .join("");
}
