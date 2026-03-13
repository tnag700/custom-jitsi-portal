import { describe, it, expect } from "vitest";
import { existsSync, readFileSync } from "node:fs";
import { join } from "node:path";

const SRC_DIR = join(__dirname, "..");
const PROJECT_DIR = join(SRC_DIR, "..");

function readSrc(relativePath: string): string {
  const full = join(SRC_DIR, relativePath);
  if (!existsSync(full)) {
    throw new Error(`File not found: ${relativePath}`);
  }
  return readFileSync(full, "utf-8");
}

function readProject(relativePath: string): string {
  const full = join(PROJECT_DIR, relativePath);
  if (!existsSync(full)) {
    throw new Error(`File not found: ${relativePath}`);
  }
  return readFileSync(full, "utf-8");
}

describe("Shared UI Guard: dependency (AC: 1-4)", () => {
  it("package.json should keep @qwik-ui/headless in dependencies without duplicate declaration", () => {
    const pkgRaw = readProject("package.json");
    const pkg = JSON.parse(pkgRaw) as {
      dependencies?: Record<string, string>;
      devDependencies?: Record<string, string>;
    };
    expect(pkg.dependencies?.["@qwik-ui/headless"]).toBeTruthy();
    expect(pkg.devDependencies?.["@qwik-ui/headless"]).toBeUndefined();
  });

  it("installed @qwik-ui/headless package metadata should expose a root export map", () => {
    const pkgRaw = readProject("node_modules/@qwik-ui/headless/package.json");
    const pkg = JSON.parse(pkgRaw) as {
      name?: string;
      exports?: Record<string, unknown>;
    };

    expect(pkg.name).toBe("@qwik-ui/headless");
    expect(pkg.exports).toBeTruthy();
    expect(pkg.exports).toHaveProperty(".");
  });
});

describe("Shared UI Guard: centralized typing and a11y contracts (AC: 1, 3)", () => {
  it("wrapper files should not declare local ShimComponent or duplicate unknown casts", () => {
    const dialog = readSrc("lib/shared/ui/AppDialog.tsx");
    const popover = readSrc("lib/shared/ui/AppPopover.tsx");
    const combobox = readSrc("lib/shared/ui/AppCombobox.tsx");

    expect(dialog).not.toContain("type ShimComponent");
    expect(popover).not.toContain("type ShimComponent");
    expect(combobox).not.toContain("type ShimComponent");
    expect(dialog).not.toContain("as unknown as ShimComponent");
    expect(popover).not.toContain("as unknown as ShimComponent");
    expect(combobox).not.toContain("as unknown as ShimComponent");
  });

  it("headless-typing.ts should define a documented centralized assertion boundary", () => {
    const ts = readSrc("lib/shared/ui/headless-typing.ts");
    expect(ts).toContain("asHeadlessComponent");
    expect(ts).toContain("PropsOf");
    expect(ts).toContain("assertion boundary");
  });

  it("a11y.ts should define shared defaults and focus-visible contract", () => {
    const ts = readSrc("lib/shared/ui/a11y.ts");
    expect(ts).toContain("dialogA11yDefaults");
    expect(ts).toContain("popoverA11yDefaults");
    expect(ts).toContain("comboboxA11yDefaults");
    expect(ts).toContain("sharedFocusVisibleContract");
    expect(ts).toContain("sharedFocusVisibleAttrs");
    expect(ts).toContain("global.css");
  });
});

describe("Shared UI Guard: AppDialog (AC: 1, 4)", () => {
  it("AppDialog.tsx should contain Modal import and component$", () => {
    const tsx = readSrc("lib/shared/ui/AppDialog.tsx");
    expect(tsx).toContain("@qwik-ui/headless");
    expect(tsx).toContain("Modal");
    expect(tsx).toContain("component$");
  });

  it("AppDialog.tsx should use design tokens and Modal.Title", () => {
    const tsx = readSrc("lib/shared/ui/AppDialog.tsx");
    expect(tsx).toContain("bg-surface");
    expect(tsx).toContain("rounded-xl");
    expect(tsx).toContain("shadow-xl");
    expect(tsx).toContain("ModalTitle");
  });

  it("AppDialog.tsx should include trigger and close labels for accessibility and i18n flexibility", () => {
    const tsx = readSrc("lib/shared/ui/AppDialog.tsx");
    expect(tsx).toContain("showTrigger = true");
    expect(tsx).toContain("triggerLabel");
    expect(tsx).toContain("closeLabel");
    expect(tsx).toContain("dialogA11yDefaults");
    expect(tsx).toContain("sharedFocusVisibleAttrs");
    expect(tsx).toContain("aria-label={triggerLabel}");
    expect(tsx).toContain("showTrigger");
    expect(tsx).toContain("ModalClose");
    expect(tsx).toContain("bind:show");
  });
});

describe("Shared UI Guard: AppCombobox (AC: 2, 4)", () => {
  it("AppCombobox.tsx should contain Combobox import and component$", () => {
    const tsx = readSrc("lib/shared/ui/AppCombobox.tsx");
    expect(tsx).toContain("@qwik-ui/headless");
    expect(tsx).toContain("Combobox");
    expect(tsx).toContain("component$");
  });

  it("AppCombobox.tsx should contain Input/Item/ItemLabel and design tokens", () => {
    const tsx = readSrc("lib/shared/ui/AppCombobox.tsx");
    expect(tsx).toContain("ComboboxInput");
    expect(tsx).toContain("ComboboxItem");
    expect(tsx).toContain("ComboboxItemLabel");
    expect(tsx).toContain("bg-surface");
    expect(tsx).toContain("border-border");
    expect(tsx).toContain("text-text");
  });

  it("AppCombobox.tsx should use generic props for value and onChange$", () => {
    const tsx = readSrc("lib/shared/ui/AppCombobox.tsx");
    expect(tsx).toContain("export interface AppComboboxProps<TValue extends string = string>");
    expect(tsx).toContain("onChange$?: QRL<(value: TValue) => void>");
    expect(tsx).toContain("comboboxA11yDefaults");
    expect(tsx).toContain("sharedFocusVisibleAttrs");
    expect(tsx).toContain("forwardComboboxValue");
    expect(tsx).toContain("filter");
    expect(tsx).toContain("onChange$={async (nextValue: unknown)");
  });
});

describe("Shared UI Guard: AppPopover (AC: 3, 4)", () => {
  it("AppPopover.tsx should contain Popover import and component$", () => {
    const tsx = readSrc("lib/shared/ui/AppPopover.tsx");
    expect(tsx).toContain("@qwik-ui/headless");
    expect(tsx).toContain("Popover");
    expect(tsx).toContain("component$");
  });

  it("AppPopover.tsx should contain Trigger/Panel and design tokens", () => {
    const tsx = readSrc("lib/shared/ui/AppPopover.tsx");
    expect(tsx).toContain("PopoverTrigger");
    expect(tsx).toContain("PopoverPanel");
    expect(tsx).toContain("bg-surface");
    expect(tsx).toContain("border-border");
    expect(tsx).toContain("shadow-2");
    expect(tsx).toContain("rounded-xl");
  });

  it("AppPopover.tsx should type floating as Placement", () => {
    const tsx = readSrc("lib/shared/ui/AppPopover.tsx");
    expect(tsx).toContain("type Placement = Exclude<PropsOf<typeof Popover.Root>[\"floating\"], boolean | undefined>");
    expect(tsx).toContain("floating?: Placement");
    expect(tsx).toContain("popoverA11yDefaults");
    expect(tsx).toContain("sharedFocusVisibleAttrs");
    expect(tsx).toContain("gutter = 8");
  });
});

describe("Shared UI Guard: barrel exports and global focus ring (AC: 4, 5)", () => {
  it("shared/ui/index.ts should export AppDialog, AppCombobox, AppPopover", () => {
    const ts = readSrc("lib/shared/ui/index.ts");
    expect(ts).toContain("AppDialog");
    expect(ts).toContain("AppCombobox");
    expect(ts).toContain("AppPopover");
  });

  it("global.css should contain focus-visible outline based on var(--primary)", () => {
    const css = readSrc("global.css");
    expect(css).toContain(":focus-visible");
    expect(css).toContain("outline: 3px solid var(--primary)");
  });
});
