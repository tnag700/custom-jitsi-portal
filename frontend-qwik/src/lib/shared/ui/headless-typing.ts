import type { JSXOutput, PropsOf } from "@qwik.dev/core";

type HeadlessPrimitiveProps<TComponent> = PropsOf<TComponent>;
type CompatibleHeadlessProps = Record<string, unknown> & {
  children?: unknown;
};

export type HeadlessComponent<TProps extends object> = (
  props: TProps,
  key: string | null,
  flags: number,
  dev?: unknown,
) => JSXOutput;

/**
 * Centralized assertion boundary for thin shared UI wrappers around @qwik-ui/headless.
 *
 * Qwik UI primitives currently expose types that do not line up cleanly with the JSX-compatible
 * signature our wrappers need, so the narrow cast lives here once instead of being copy-pasted in
 * every wrapper file.
 */
export function asHeadlessComponent<TComponent>(
  component: TComponent,
): HeadlessComponent<CompatibleHeadlessProps> {
  void (0 as unknown as HeadlessPrimitiveProps<TComponent>);
  return component as unknown as HeadlessComponent<CompatibleHeadlessProps>;
}