/* eslint-disable @typescript-eslint/ban-ts-comment */
// @ts-nocheck
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { renderNode } from "./support/jsx-tree";
import { JoinPage } from "../routes/join-page";

const state = vi.hoisted(() => ({
  signals: [], index: 0, tasks: [], visibleTasks: [], props: {},
  snapshot: { status: "ready", checkedAt: "2026-09-28", publicJoinUrl: "https://meet.example/", systemChecks: [] },
  action: { isRunning: false, value: undefined, submit: vi.fn() },
  readiness: vi.fn(), browser: vi.fn(),
}));

vi.mock("@qwik.dev/core", async (importOriginal) => {
  const actual = await importOriginal();
  const identity = (value) => value;
  actual.noSerialize(state.action.submit);
  return {
    ...actual,
    get _captures() { return actual._captures; },
    component$: identity, componentQrl: identity,
    useSignal: (value) => state.signals[state.index++] ??= { value },
    useTask$: (task) => state.tasks.push(task),
    useTaskQrl: (task) => state.tasks.push(task),
    useVisibleTask$: (task) => state.visibleTasks.push(task),
    useVisibleTaskQrl: (task) => state.visibleTasks.push(task),
  };
});
vi.mock("../routes/join-loaders", () => ({
  useUpcomingMeetings: () => ({ value: { meetings: [], loadError: null } }),
  useJoinRuntimeConfig: () => ({ value: { publicApiUrl: "https://portal.example/api/v1" } }),
  useJoinReadiness: () => ({ value: state.snapshot }),
}));
vi.mock("../routes/join-action", () => ({ useJoinMeeting: () => state.action }));
vi.mock("~/lib/domains/join", async (importOriginal) => {
  const actual = await importOriginal();
  return {
    ...actual, fetchJoinReadiness: state.readiness, runBrowserPreflight: state.browser,
    UpcomingMeetingsList: (props) => { state.props.list = props; return null; },
    JoinErrorPanel: (props) => { state.props.error = props; return null; },
    JoinPreflightPanel: (props) => { state.props.preflight = props; return null; },
  };
});

async function renderPage() {
  state.index = 0;
  await renderNode(JoinPage());
}

function deferred() {
  let resolve;
  const promise = new Promise((done) => { resolve = done; });
  return { promise, resolve };
}

beforeEach(() => {
  state.signals = []; state.tasks = []; state.visibleTasks = []; state.props = {};
  state.action.isRunning = false; state.action.value = undefined;
  state.action.submit.mockReset().mockResolvedValue({ value: { error: {
    title: "Нет соединения", detail: "Повторите вход", errorCode: "NETWORK_UNREACHABLE",
  } } });
  state.readiness.mockReset().mockResolvedValue(state.snapshot);
  state.browser.mockReset().mockResolvedValue({ systemChecks: [], mediaChecks: [] });
  vi.stubGlobal("window", { location: { assign: vi.fn() } });
});
afterEach(() => vi.unstubAllGlobals());

describe("join page lifecycle", () => {
  it("schedules browser diagnostics after an SSR render", async () => {
    vi.stubGlobal("window", undefined);
    await renderPage();
    for (const task of state.tasks) await task({ track: (read) => read() });
    expect(state.browser).not.toHaveBeenCalled();
    vi.stubGlobal("window", { location: { assign: vi.fn() } });
    for (const task of state.visibleTasks) await task({});
    expect(state.browser).toHaveBeenCalledExactlyOnceWith({ publicJoinUrl: "https://meet.example/", scope: "full" });
  });

  it("serializes retry, join and refresh before asynchronous diagnostics complete", async () => {
    await renderPage();
    await state.props.list.onJoin$("meeting-1");
    await renderPage();
    const pending = deferred();
    state.readiness.mockReturnValue(pending.promise);
    const retry = state.props.error.onRetry$();
    const duplicate = state.props.error.onRetry$();
    const join = state.props.list.onJoin$("meeting-2");
    const refresh = state.props.preflight.onRefresh$();
    await renderPage();
    expect(state.props.list.disabled).toBe(true);
    expect(state.props.error.retryDisabled).toBe(true);
    expect(state.props.preflight.running).toBe(true);
    pending.resolve(state.snapshot);
    await Promise.all([retry, duplicate, join, refresh]);
    expect(state.readiness).toHaveBeenCalledTimes(1);
    expect(state.action.submit.mock.calls.map(([input]) => input.meetingId)).toEqual(["meeting-1", "meeting-1"]);
    await state.props.error.onRetry$();
    await state.props.error.onRetry$();
    expect(state.action.submit).toHaveBeenCalledTimes(3);
  });

  it("preserves the server join error instead of treating it as a redirect", async () => {
    await renderPage();
    await state.props.list.onJoin$("meeting-1");
    await renderPage();
    expect(state.props.error.error.errorCode).toBe("NETWORK_UNREACHABLE");
  });

  it("releases the join guard after a rejected action and offers a retry", async () => {
    await renderPage();
    state.action.submit.mockRejectedValueOnce(new Error("network lost"));
    await expect(state.props.list.onJoin$("meeting-1")).resolves.toBeUndefined();
    await renderPage();
    expect(state.props.error.error.errorCode).toBe("NETWORK_UNREACHABLE");
    expect(state.props.list.disabled).toBe(false);
    await state.props.error.onRetry$();
    expect(state.action.submit).toHaveBeenCalledTimes(2);
  });

  it("releases its guard and displays readiness failure without submitting", async () => {
    await renderPage();
    await state.props.list.onJoin$("meeting-1");
    await renderPage();
    state.readiness.mockRejectedValueOnce(new Error("offline"));
    await expect(state.props.error.onRetry$()).resolves.toBeUndefined();
    await renderPage();
    expect(state.props.error.error.errorCode).toBe("JOIN_READINESS_UNAVAILABLE");
    expect(state.action.submit).toHaveBeenCalledTimes(1);
    await state.props.preflight.onRefresh$();
    expect(state.props.preflight.running).toBe(false);
  });
});
