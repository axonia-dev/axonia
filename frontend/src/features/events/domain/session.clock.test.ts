import { describe, expect, it } from "vitest";

import type { Session } from "@/features/events/domain/session";
import { isRunning, nextSessionMark } from "@/features/events/domain/session";
import { aScheduledSession } from "@/test/session-fixture";

/** A session meeting at 18:00 UTC for an hour, so its window closes at 19:00. */
function anHourLong(overrides: Partial<Session> = {}): Session {
  return aScheduledSession({
    scheduledAt: "2026-08-15T18:00:00Z",
    durationMinutes: 60,
    ...overrides,
  });
}

function at(iso: string): Date {
  return new Date(iso);
}

describe("isRunning", () => {
  it("is true inside the session's own window", () => {
    expect(isRunning(anHourLong(), at("2026-08-15T18:30:00Z"))).toBe(true);
  });

  it("is false before it starts", () => {
    expect(isRunning(anHourLong(), at("2026-08-15T17:59:00Z"))).toBe(false);
  });

  it("is false at the instant the window closes", () => {
    expect(isRunning(anHourLong(), at("2026-08-15T19:00:00Z"))).toBe(false);
  });

  it("is false when nobody recorded how long it runs, so no end is known", () => {
    const noLength = anHourLong({ durationMinutes: null });
    expect(isRunning(noLength, at("2026-08-15T18:30:00Z"))).toBe(false);
  });

  it("is false for a session nobody set an hour for", () => {
    expect(
      isRunning(anHourLong({ hasTime: false }), at("2026-08-15T18:30:00Z")),
    ).toBe(false);
  });
});

describe("nextSessionMark", () => {
  it("waits for the start while the session is still ahead", () => {
    expect(nextSessionMark(anHourLong(), at("2026-08-15T17:00:00Z"))).toBe(
      at("2026-08-15T18:00:00Z").getTime(),
    );
  });

  it("waits for the end once the session is running", () => {
    expect(nextSessionMark(anHourLong(), at("2026-08-15T18:30:00Z"))).toBe(
      at("2026-08-15T19:00:00Z").getTime(),
    );
  });

  it("waits for nothing once the window has closed", () => {
    expect(
      nextSessionMark(anHourLong(), at("2026-08-15T19:30:00Z")),
    ).toBeNull();
  });

  it("waits for nothing past the start of a session with no known end", () => {
    const noLength = anHourLong({ durationMinutes: null });
    expect(nextSessionMark(noLength, at("2026-08-15T18:30:00Z"))).toBeNull();
  });

  it("waits for nothing on a session that has already settled", () => {
    const held = anHourLong({ status: "HELD" });
    expect(nextSessionMark(held, at("2026-08-15T17:00:00Z"))).toBeNull();
  });
});
