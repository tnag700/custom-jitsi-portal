import { describe, it, expect } from "vitest";
import { existsSync, readFileSync } from "node:fs";
import { join } from "node:path";

const SRC_DIR = join(__dirname, "..");

function readSrc(relativePath: string): string {
  const full = join(SRC_DIR, relativePath);
  if (!existsSync(full)) {
    throw new Error(`File not found: ${relativePath}`);
  }
  return readFileSync(full, "utf-8");
}

describe("Join Flow Guard: types (AC: 1, 2, 3)", () => {
  it("types.ts should define UpcomingMeetingCard, JoinErrorPayload, MeetingAccessTokenResponse", () => {
    const ts = readSrc("lib/domains/join/types.ts");
    expect(ts).toContain("UpcomingMeetingCard");
    expect(ts).toContain("JoinErrorPayload");
    expect(ts).toContain("MeetingAccessTokenResponse");
  });
});

describe("Join Flow Guard: service (AC: 1, 2, 3, 6)", () => {
  it("join.service.ts should contain fetchUpcomingMeetings, issueAccessToken, JoinServiceError", () => {
    const ts = readSrc("lib/domains/join/join.service.ts");
    expect(ts).toContain("fetchUpcomingMeetings");
    expect(ts).toContain("issueAccessToken");
    expect(ts).toContain("JoinServiceError");
  });
});

describe("Join Flow Guard: components (AC: 1, 2, 3, 4, 5)", () => {
  it("UpcomingMeetingsList.tsx should contain component$", () => {
    const tsx = readSrc("lib/domains/join/components/UpcomingMeetingsList.tsx");
    expect(tsx).toContain("component$");
  });

  it("UpcomingMeetingCard.tsx should contain component$", () => {
    const tsx = readSrc("lib/domains/join/components/UpcomingMeetingCard.tsx");
    expect(tsx).toContain("component$");
  });

  it("JoinErrorPanel.tsx should contain component$", () => {
    const tsx = readSrc("lib/domains/join/components/JoinErrorPanel.tsx");
    expect(tsx).toContain("component$");
  });
});

describe("Join Flow Guard: barrel export (AC: all)", () => {
  it("index.ts should exist as join barrel export", () => {
    const ts = readSrc("lib/domains/join/index.ts");
    expect(ts).toBeDefined();
  });
});

describe("Join Flow Guard: route integration (AC: 1, 2, 3, 4, 5, 6)", () => {
  it("routes/index.tsx should stay a thin composition boundary", () => {
    const tsx = readSrc("routes/index.tsx");
    expect(tsx).toContain("JoinPage");
    expect(tsx).toContain("useUpcomingMeetings");
    expect(tsx).toContain("useJoinMeeting");
    expect(tsx).not.toContain("routeLoader$");
    expect(tsx).not.toContain("routeAction$");
    expect(tsx.split("\n").length).toBeLessThan(40);
  });

  it("join loaders should own upcoming meetings and readiness requests", () => {
    const ts = readSrc("routes/join-loaders.ts");
    expect(ts).toContain("routeLoader$");
    expect(ts).toContain("fetchUpcomingMeetings");
    expect(ts).toContain("fetchJoinReadiness");
    expect(ts).toContain("mapUpcomingMeetingsLoaderError");
    expect(ts).toContain("loadError");
  });

  it("join action should issue access tokens through the shared mutation context", () => {
    const ts = readSrc("routes/join-action.ts");
    expect(ts).toContain("routeAction$");
    expect(ts).toContain("issueAccessToken");
    expect(ts).toContain("buildMutationRequestContext");
  });

  it("join action should handle AUTH_REQUIRED redirect", () => {
    const ts = readSrc("routes/join-action.ts");
    expect(ts).toContain("AUTH_REQUIRED");
    expect(ts).toContain("resolveAuthRecoveryRedirectPath(error, returnTo)");
  });

  it("join page should keep meeting context for retry after action errors", () => {
    const tsx = readSrc("routes/join-page.tsx");
    expect(tsx).not.toContain("joiningMeetingId.value = null;");
  });

  it("join page should bind loading state to joinAction.isRunning", () => {
    const tsx = readSrc("routes/join-page.tsx");
    expect(tsx).toContain(
      "joinAction.isRunning ? joiningMeetingId.value : null",
    );
  });

  it("join page should guard against double submit before submit call", () => {
    const tsx = readSrc("routes/join-page.tsx");
    expect(tsx).toContain("if (!canStartJoin(joinAction.isRunning))");
  });

  it("join redirect helper should reject unsafe joinUrl before navigation", () => {
    const ts = readSrc("lib/domains/join/join-flow.helpers.ts");
    const tsx = readSrc("routes/join-page.tsx");
    expect(ts).toContain("validateJoinRedirect(");
    expect(ts).toContain("JOIN_RESPONSE_INVALID");
    expect(ts).toContain("url.origin !== expectedOrigin");
    expect(tsx).toContain("window.location.assign(validated.joinUrl)");
  });
});

describe("Join Flow Guard: concurrency protection (M3 fix)", () => {
  it("UpcomingMeetingCard.tsx should accept disabled prop to block parallel joins", () => {
    const tsx = readSrc("lib/domains/join/components/UpcomingMeetingCard.tsx");
    expect(tsx).toContain("disabled");
  });

  it("UpcomingMeetingsList.tsx should propagate disabled prop to cards", () => {
    const tsx = readSrc("lib/domains/join/components/UpcomingMeetingsList.tsx");
    expect(tsx).toContain("disabled");
  });

  it("join page should disable all cards while any join is in flight", () => {
    const tsx = readSrc("routes/join-page.tsx");
    expect(tsx).toContain("disabled={joinAction.isRunning}");
  });
});

describe("Join Flow Guard: clipboard feedback (L2 fix)", () => {
  it("join page should track clipboard copy state via signal", () => {
    const tsx = readSrc("routes/join-page.tsx");
    expect(tsx).toContain("clipboardCopied");
  });

  it("JoinErrorPanel.tsx should accept reportCopied prop for visual confirmation", () => {
    const tsx = readSrc("lib/domains/join/components/JoinErrorPanel.tsx");
    expect(tsx).toContain("reportCopied");
  });

  it("JoinErrorPanel.tsx should delegate retry/escalation actions to shared component", () => {
    const tsx = readSrc("lib/domains/join/components/JoinErrorPanel.tsx");
    expect(tsx).toContain("RetryEscalationActions");
  });
});

describe("Join Flow Guard: reusable request state UX", () => {
  it("shared RequestStatePanel component should exist", () => {
    const tsx = readSrc("lib/shared/components/RequestStatePanel.tsx");
    expect(tsx).toContain("component$");
    expect(tsx).toContain("tone");
  });

  it("join page should render RequestStatePanel on loader error", () => {
    const tsx = readSrc("routes/join-page.tsx");
    expect(tsx).toContain("RequestStatePanel");
    expect(tsx).toContain("meetingsState.value.loadError");
  });

  it("shared RetryEscalationActions component should exist", () => {
    const tsx = readSrc("lib/shared/components/RetryEscalationActions.tsx");
    expect(tsx).toContain("component$");
    expect(tsx).toContain("canRetry");
  });
});
