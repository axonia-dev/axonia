import { describe, expect, it } from "vitest";

import {
  isJoinOpen,
  joinPhase,
  nextPhaseChange,
} from "@/features/events/domain/join-window";

const START = "2026-08-18T16:00:00Z";

function at(iso: string) {
  return joinPhase({
    scheduledAt: START,
    durationMinutes: 60,
    now: new Date(iso),
  });
}

function changeAfter(iso: string) {
  return nextPhaseChange({
    scheduledAt: START,
    durationMinutes: 60,
    now: new Date(iso),
  });
}

describe("joinPhase", () => {
  it("is far more than an hour out", () => {
    expect(at("2026-08-18T14:30:00Z")).toBe("far");
  });

  it("is soon at exactly sixty minutes out", () => {
    expect(at("2026-08-18T15:00:00Z")).toBe("soon");
  });

  it("is still soon at six minutes out", () => {
    expect(at("2026-08-18T15:54:00Z")).toBe("soon");
  });

  it("opens at exactly five minutes out", () => {
    expect(at("2026-08-18T15:55:00Z")).toBe("open");
  });

  it("is live from the scheduled instant", () => {
    expect(at("2026-08-18T16:00:00Z")).toBe("live");
  });

  it("is still live one minute before the end", () => {
    expect(at("2026-08-18T16:59:00Z")).toBe("live");
  });

  it("is over at the end", () => {
    expect(at("2026-08-18T17:00:00Z")).toBe("over");
  });

  it("falls back to ninety minutes where no length is recorded", () => {
    const phase = joinPhase({
      scheduledAt: START,
      durationMinutes: null,
      now: new Date("2026-08-18T17:20:00Z"),
    });

    expect(phase).toBe("live");
  });
});

describe("isJoinOpen", () => {
  it("reaches the way in during the window it is for", () => {
    expect(isJoinOpen("open")).toBe(true);
    expect(isJoinOpen("live")).toBe(true);
  });

  it("keeps the way in shut outside that window", () => {
    expect(isJoinOpen("far")).toBe(false);
    expect(isJoinOpen("soon")).toBe(false);
    expect(isJoinOpen("over")).toBe(false);
  });
});

describe("nextPhaseChange", () => {
  it("points a far session at the instant it turns soon", () => {
    expect(changeAfter("2026-08-18T14:30:00Z")).toBe(
      Date.parse("2026-08-18T15:00:00Z"),
    );
  });

  it("points a soon session at the instant the way in opens", () => {
    expect(changeAfter("2026-08-18T15:30:00Z")).toBe(
      Date.parse("2026-08-18T15:55:00Z"),
    );
  });

  it("points an open session at the scheduled instant", () => {
    expect(changeAfter("2026-08-18T15:56:00Z")).toBe(
      Date.parse("2026-08-18T16:00:00Z"),
    );
  });

  it("points a live session at its end", () => {
    expect(changeAfter("2026-08-18T16:20:00Z")).toBe(
      Date.parse("2026-08-18T17:00:00Z"),
    );
  });

  it("has nothing left to wait for once the session is over", () => {
    expect(changeAfter("2026-08-18T17:00:00Z")).toBeNull();
  });

  it("ends a session of unrecorded length ninety minutes in", () => {
    const change = nextPhaseChange({
      scheduledAt: START,
      durationMinutes: null,
      now: new Date("2026-08-18T16:20:00Z"),
    });

    expect(change).toBe(Date.parse("2026-08-18T17:30:00Z"));
  });
});
