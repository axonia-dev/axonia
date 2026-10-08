import { describe, expect, it } from "vitest";

import {
  sessionPhase,
  sessionStatusChip,
} from "@/features/events/domain/session-phase";
import { aScheduledSession } from "@/test/session-fixture";

const NOW = new Date("2026-08-20T12:00:00Z");
const PAST = "2026-08-15T18:00:00Z";
const FUTURE = "2026-08-25T18:00:00Z";

describe("sessionPhase", () => {
  it("is upcoming for a scheduled session whose hour has not come", () => {
    const session = aScheduledSession({ scheduledAt: FUTURE });
    expect(sessionPhase(session, NOW)).toBe("upcoming");
  });

  it("is awaiting for a scheduled session whose hour has passed", () => {
    const session = aScheduledSession({ scheduledAt: PAST });
    expect(sessionPhase(session, NOW)).toBe("awaiting");
  });

  it("is held once an organizer has said it happened", () => {
    const session = aScheduledSession({ status: "HELD", scheduledAt: PAST });
    expect(sessionPhase(session, NOW)).toBe("held");
  });

  it("is closed for a cancelled session, whatever its date", () => {
    const session = aScheduledSession({
      status: "CANCELLED",
      scheduledAt: FUTURE,
    });
    expect(sessionPhase(session, NOW)).toBe("closed");
  });

  it("is closed for a skipped session", () => {
    const session = aScheduledSession({ status: "SKIPPED" });
    expect(sessionPhase(session, NOW)).toBe("closed");
  });

  it("stays awaiting while a session is still literally running, so the close panel does not move (R26)", () => {
    const session = aScheduledSession({
      scheduledAt: "2026-08-20T11:30:00Z",
      durationMinutes: 60,
    });
    expect(sessionPhase(session, NOW)).toBe("awaiting");
  });
});

describe("sessionStatusChip", () => {
  it("states that a held session was held", () => {
    const session = aScheduledSession({ status: "HELD", scheduledAt: PAST });
    expect(sessionStatusChip(session, NOW)).toEqual({
      tone: "success",
      label: "Held",
    });
  });

  it("states cancellation in danger tone", () => {
    const session = aScheduledSession({ status: "CANCELLED" });
    expect(sessionStatusChip(session, NOW)).toEqual({
      tone: "danger",
      label: "Cancelled",
    });
  });

  it("states a skipped occurrence neutrally, because nothing went wrong", () => {
    const session = aScheduledSession({ status: "SKIPPED" });
    expect(sessionStatusChip(session, NOW)).toEqual({
      tone: "neutral",
      label: "Skipped",
    });
  });

  it("asks for an answer once the hour has passed and nobody has given one", () => {
    const session = aScheduledSession({ scheduledAt: PAST });
    expect(sessionStatusChip(session, NOW)).toEqual({
      tone: "warning",
      label: "Awaiting an answer",
    });
  });

  it("counts the days down for a session still to come", () => {
    const session = aScheduledSession({ scheduledAt: FUTURE });
    expect(sessionStatusChip(session, NOW)).toEqual({
      tone: "neutral",
      label: "In 5 days",
    });
  });

  it("says tomorrow rather than in 1 day", () => {
    const session = aScheduledSession({ scheduledAt: "2026-08-21T12:00:00Z" });
    expect(sessionStatusChip(session, NOW)).toEqual({
      tone: "neutral",
      label: "Tomorrow",
    });
  });

  it("says a session inside the hour is starting soon", () => {
    const session = aScheduledSession({ scheduledAt: "2026-08-20T12:30:00Z" });
    expect(sessionStatusChip(session, NOW)).toEqual({
      tone: "success",
      label: "Starting soon",
    });
  });

  it("reads a session later the same day as later today, not as tomorrow", () => {
    const session = aScheduledSession({ scheduledAt: "2026-08-20T20:00:00Z" });
    expect(sessionStatusChip(session, NOW)).toEqual({
      tone: "neutral",
      label: "Later today",
    });
  });

  it("counts the days on the community's clock rather than the reader's", () => {
    const session = aScheduledSession({ scheduledAt: "2026-08-21T01:00:00Z" });
    expect(sessionStatusChip(session, NOW)).toEqual({
      tone: "neutral",
      label: "Tomorrow",
    });
  });

  it("reads running now while a session is inside its own scheduled window (R26)", () => {
    const session = aScheduledSession({
      scheduledAt: "2026-08-20T11:30:00Z",
      durationMinutes: 60,
    });
    expect(sessionStatusChip(session, NOW)).toEqual({
      tone: "success",
      label: "Running now",
    });
  });

  it("reads awaiting an answer once a session's own window has ended (R26)", () => {
    const session = aScheduledSession({
      scheduledAt: "2026-08-20T10:00:00Z",
      durationMinutes: 60,
    });
    expect(sessionStatusChip(session, NOW)).toEqual({
      tone: "warning",
      label: "Awaiting an answer",
    });
  });

  /**
   * The end is exclusive: at the instant the window closes the session reads as owing an answer
   * rather than as still running, the same convention the run sheet's own planned-end marker uses
   * for a section that starts exactly on the budget (`markerIndex`'s `start >= budget`).
   */
  it("treats the exact end of the window as over, not as still running (R26)", () => {
    const session = aScheduledSession({
      scheduledAt: "2026-08-20T11:00:00Z",
      durationMinutes: 60,
    });
    expect(sessionStatusChip(session, NOW)).toEqual({
      tone: "warning",
      label: "Awaiting an answer",
    });
  });

  it("never reads running now for a session with no recorded length, even mid-window (R26)", () => {
    const session = aScheduledSession({
      scheduledAt: "2026-08-20T11:30:00Z",
      durationMinutes: null,
    });
    expect(sessionStatusChip(session, NOW)).toEqual({
      tone: "warning",
      label: "Awaiting an answer",
    });
  });
});
