import { describe, expect, it, vi } from "vitest";

vi.mock("@qwik.dev/core", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@qwik.dev/core")>();
  const identity = <T>(value: T): T => value;
  const noop = () => undefined;
  return {
    ...actual,
    $: identity,
    inlinedQrl: identity,
    inlinedQrlDEV: identity,
    qrl: identity,
    component$: identity,
    componentQrl: identity,
    Slot: noop,
    useSignal: <T>(value: T) => ({ value }),
    useTask$: noop,
    useVisibleTask$: noop,
    useContext: () => ({}),
    useStore: <T extends object>(value: T) => value,
    useComputed$: identity,
    createContextId: (id: string) => id,
  };
});

vi.mock("@qwik.dev/router", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@qwik.dev/router")>();
  const identity = <T>(value: T): T => value;
  return {
    ...actual,
    Link: () => null,
    routeLoader$: identity,
    routeLoaderQrl: identity,
    routeAction$: identity,
    routeActionQrl: identity,
    Form: () => null,
    z: {
      object: (schema: unknown) => schema,
      string: () => ({ min: () => ({}) }),
      number: () => ({ int: () => ({ positive: () => ({}) }) }),
      boolean: () => ({}),
      enum: () => ({}),
      union: () => ({}),
      literal: () => ({}),
      optional: () => ({}),
      array: () => ({ min: () => ({}) }),
    },
    zod$: identity,
  };
});

vi.mock("@qwik-ui/headless", () => {
  const primitive = () => null;
  return {
    Modal: {
      Root: primitive,
      Trigger: primitive,
      Panel: primitive,
      Title: primitive,
      Description: primitive,
      Close: primitive,
    },
    Combobox: {
      Root: primitive,
      Label: primitive,
      Control: primitive,
      Input: primitive,
      Trigger: primitive,
      Popover: primitive,
      Item: primitive,
      ItemLabel: primitive,
      ItemIndicator: primitive,
    },
    Popover: {
      Root: primitive,
      Trigger: primitive,
      Panel: primitive,
    },
  };
});

describe("routes and shared ui runtime smoke", () => {
  it("imports shared ui modules", async () => {
    const uiIndex = await import("~/lib/shared/ui");
    const appDialog = await import("~/lib/shared/ui/AppDialog");
    const appCombobox = await import("~/lib/shared/ui/AppCombobox");
    const appPopover = await import("~/lib/shared/ui/AppPopover");

    expect(uiIndex).toHaveProperty("AppDialog");
    expect(uiIndex).toHaveProperty("AppCombobox");
    expect(uiIndex).toHaveProperty("AppPopover");
    expect(appDialog).toHaveProperty("AppDialog");
    expect(appCombobox).toHaveProperty("AppCombobox");
    expect(appPopover).toHaveProperty("AppPopover");
  });

  it("imports route modules and critical exports", async () => {
    const root = await import("~/routes/index");
    const layout = await import("~/routes/layout");
    const auth = await import("~/routes/auth/index");
    const authContinue = await import("~/routes/auth/continue/index");
    const meetings = await import("~/routes/meetings/index");
    const rooms = await import("~/routes/rooms/index");
    const profile = await import("~/routes/profile/index");
    const invite = await import("~/routes/invite/[inviteToken]/index");

    expect(root).toHaveProperty("default");
    expect(root).toHaveProperty("useUpcomingMeetings");
    expect(root).toHaveProperty("useJoinMeeting");
    expect(layout).toHaveProperty("default");
    expect(auth).toHaveProperty("default");
    expect(authContinue).toHaveProperty("default");
    expect(meetings).toHaveProperty("default");
    expect(meetings).toHaveProperty("useMeetings");
    expect(rooms).toHaveProperty("default");
    expect(rooms).toHaveProperty("useRooms");
    expect(profile).toHaveProperty("default");
    expect(profile).toHaveProperty("useMyProfile");
    expect(invite).toHaveProperty("default");
    expect(invite).toHaveProperty("useInviteTokenLoader");
  });
});
