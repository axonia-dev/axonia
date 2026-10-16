import { describe, expect, it } from "vitest";

import {
  countdownParts,
  countdownPhase,
  countdownSpeech,
  countdownWakeAt,
  endOfEntry,
  hasEnded,
  lastHourLabel,
  type Timed,
} from "@/features/events/domain/next-countdown";

const START = "2026-10-18T14:00:00Z";
const startMs = new Date(START).getTime();
const entry: Timed = { scheduledAt: START, hasTime: true, durationMinutes: 60 };
const at = (offsetMs: number) => new Date(startMs + offsetMs);
const MIN = 60_000;

describe("countdown phases", () => {
  it("counts days out and wakes at the next whole second", () => {
    const now = new Date(startMs - 2 * 86_400_000 + 400);
    expect(countdownPhase(entry, now)).toEqual({
      kind: "counting",
      msLeft: 2 * 86_400_000 - 400,
    });
    expect(countdownWakeAt(entry, now)).toBe(
      Math.floor(now.getTime() / 1000) * 1000 + 1000,
    );
  });

  it("enters the last hour", () => {
    const now = at(-(42 * MIN + 10_000));
    expect(countdownPhase(entry, now)?.kind).toBe("last-hour");
    expect(lastHourLabel(42 * MIN + 10_000)).toBe("Starts in 42:10");
  });

  it("runs from the start until the end and wakes at the end", () => {
    for (const now of [at(0), at(59 * MIN)]) {
      expect(countdownPhase(entry, now)).toEqual({ kind: "running" });
      expect(countdownWakeAt(entry, now)).toBe(startMs + 60 * MIN);
    }
  });

  it("ends at the end", () => {
    expect(countdownPhase(entry, at(60 * MIN))).toEqual({ kind: "ended" });
    expect(countdownWakeAt(entry, at(60 * MIN))).toBeNull();
    expect(hasEnded(entry, at(60 * MIN))).toBe(true);
    expect(hasEnded(entry, at(0))).toBe(false);
  });

  it.each([[{ ...entry, hasTime: false }], [{ ...entry, scheduledAt: null }]])(
    "has no phase without a start instant",
    (untimed) => {
      expect(countdownPhase(untimed, at(0))).toBeNull();
      expect(countdownWakeAt(untimed, at(0))).toBeNull();
      expect(hasEnded(untimed, at(0))).toBe(false);
      expect(endOfEntry(untimed)).toBeNull();
    },
  );

  it("never runs without a duration", () => {
    const open: Timed = { ...entry, durationMinutes: null };
    expect(endOfEntry(open)).toBe(startMs);
    expect(countdownPhase(open, at(-1000))?.kind).toBe("last-hour");
    expect(countdownPhase(open, at(0))).toEqual({ kind: "ended" });
  });
});

describe("countdown reading", () => {
  it("splits a duration into parts", () => {
    expect(
      countdownParts(2 * 86_400_000 + 7 * 3_600_000 + 11 * MIN + 58_000),
    ).toEqual({ days: 2, hours: 7, minutes: 11, seconds: 58 });
  });

  it("speaks the two largest units", () => {
    expect(countdownSpeech(2 * 86_400_000 + 7 * 3_600_000)).toBe(
      "starts in 2 days 7 hours",
    );
    expect(countdownSpeech(86_400_000 + 3_600_000)).toBe(
      "starts in 1 day 1 hour",
    );
    expect(countdownSpeech(2 * 3_600_000 + MIN)).toBe(
      "starts in 2 hours 1 minute",
    );
    expect(countdownSpeech(42 * MIN)).toBe("starts in 42 minutes");
  });
});
