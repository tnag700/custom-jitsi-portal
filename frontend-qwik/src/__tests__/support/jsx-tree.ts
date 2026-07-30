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

export function renderNode(node: unknown): unknown {
  if (Array.isArray(node)) {
    return node.map((child) => renderNode(child));
  }
  if (!isJsxLikeNode(node)) {
    return node;
  }
  if (typeof node.type === "function") {
    return renderNode(node.type(node.props ?? {}));
  }
  if (typeof node.type === "string") {
    return { type: node.type, props: node.props ?? {} };
  }
  return renderNode(node.props?.children);
}

export function collectNodes(node: unknown): RenderedNode[] {
  const rendered = renderNode(node);
  if (Array.isArray(rendered)) {
    return rendered.flatMap((child) => collectNodes(child));
  }
  if (!isRenderedNode(rendered)) {
    return [];
  }
  return [
    rendered,
    ...asArray(rendered.props.children).flatMap((child) => collectNodes(child)),
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

export function textContent(node: unknown): string {
  if (Array.isArray(node)) {
    return node.map((child) => textContent(child)).join("");
  }
  if (typeof node === "string" || typeof node === "number") {
    return String(node);
  }

  const rendered = renderNode(node);
  if (Array.isArray(rendered)) {
    return rendered.map((child) => textContent(child)).join("");
  }
  if (!isRenderedNode(rendered)) {
    return "";
  }
  return asArray(rendered.props.children)
    .map((child) => textContent(child))
    .join("");
}
