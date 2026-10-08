import { act, render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { useCloseSession } from "@/features/events/api/use-session";
import type { Session } from "@/features/events/domain/session";
import { SessionClosePanel } from "@/features/events/ui/SessionClosePanel";
import { aScheduledSession } from "@/test/session-fixture";

vi.mock("@/features/events/api/use-session", () => ({
  useCloseSession: vi.fn(),
}));

/** A session meeting at 18:00 UTC for an hour, so its window closes at 19:00. */
function anHourLong(overrides: Partial<Session> = {}): Session {
  return aScheduledSession({
    scheduledAt: "2026-08-15T18:00:00Z",
    durationMinutes: 60,
    ...overrides,
  });
}

function renderPanel(session: Session) {
  return render(
    <SessionClosePanel slug="backend-guild" session={session} canManage />,
  );
}

function askedToClose(): boolean {
  return screen.queryByRole("button", { name: "It happened" }) !== null;
}

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(useCloseSession).mockReturnValue({
    mutate: vi.fn(),
    reset: vi.fn(),
    isPending: false,
    error: null,
  } as never);
  vi.useFakeTimers();
});

afterEach(() => {
  vi.useRealTimers();
});

describe("SessionClosePanel, while the session is running", () => {
  it("holds the question back while the session is still inside its own window", () => {
    vi.setSystemTime(new Date("2026-08-15T18:30:00Z"));
    renderPanel(anHourLong());

    expect(askedToClose()).toBe(false);
  });

  it("asks the moment the window closes, without the page being reloaded", () => {
    vi.setSystemTime(new Date("2026-08-15T18:59:00Z"));
    renderPanel(anHourLong());

    act(() => {
      vi.advanceTimersByTime(60_000);
    });

    expect(askedToClose()).toBe(true);
  });

  // No recorded length means no knowable end, so the start hour is the only instant there is to
  // ask from - the same reason such a session never reads as running.
  it("still asks from the start of a session whose length nobody recorded", () => {
    vi.setSystemTime(new Date("2026-08-15T18:30:00Z"));
    renderPanel(anHourLong({ durationMinutes: null }));

    expect(askedToClose()).toBe(true);
  });
});
