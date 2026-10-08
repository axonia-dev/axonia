import { describe, expect, it } from "vitest";

import { nowMarkerState } from "@/features/events/domain/now-marker";
import { runSheet } from "@/features/events/domain/run-sheet";
import type { Session } from "@/features/events/domain/session";
import { aScheduledSession, aSection, aSlot } from "@/test/session-fixture";

/** Three blocks of 10, 15 and 10 minutes, meeting at 20:00 on the series' own clock. */
function threeBlocks(overrides: Partial<Session> = {}): Session {
  return aScheduledSession({
    scheduledAt: "2026-08-15T18:00:00Z",
    seriesTimeZone: "Europe/Paris",
    durationMinutes: 35,
    sections: [
      aSection({ id: 1, slots: [aSlot({ id: 1, durationMinutes: 10 })] }),
      aSection({ id: 2, slots: [aSlot({ id: 2, durationMinutes: 15 })] }),
      aSection({ id: 3, slots: [aSlot({ id: 3, durationMinutes: 10 })] }),
    ],
    ...overrides,
  });
}

function stateAt(iso: string, session = threeBlocks()) {
  return nowMarkerState(session, runSheet(session).rows, new Date(iso));
}

describe("nowMarkerState", () => {
  it("puts the marker in the block the clock is inside, at how far into it", () => {
    const { marker } = stateAt("2026-08-15T18:12:00Z");
    expect(marker).toEqual({ rowIndex: 1, fraction: 2 / 15, label: "20:12" });
  });

  it("sits at the very top of a block on the minute it starts", () => {
    const { marker } = stateAt("2026-08-15T18:10:00Z");
    expect(marker).toMatchObject({ rowIndex: 1, fraction: 0 });
  });

  it("reads the hour on the clock that governs the session, not the viewer's", () => {
    const { marker } = stateAt("2026-08-15T18:03:00Z");
    expect(marker?.label).toBe("20:03");
  });

  it("shows nothing before the session starts", () => {
    expect(stateAt("2026-08-15T17:59:00Z").marker).toBeNull();
  });

  it("shows nothing once the clock passes the last block", () => {
    expect(stateAt("2026-08-15T18:35:00Z").marker).toBeNull();
  });

  it("shows nothing for a session nobody set an hour for", () => {
    const session = threeBlocks({ hasTime: false });
    expect(stateAt("2026-08-15T18:12:00Z", session).marker).toBeNull();
  });

  it("shows nothing on a session that already settled", () => {
    const session = threeBlocks({ status: "HELD" });
    expect(stateAt("2026-08-15T18:12:00Z", session).marker).toBeNull();
  });

  it("shows nothing on an agenda with no blocks on it", () => {
    const session = threeBlocks({ sections: [] });
    expect(stateAt("2026-08-15T18:12:00Z", session).marker).toBeNull();
  });

  it("passes over a block nobody put any minutes on", () => {
    const session = threeBlocks({
      sections: [
        aSection({ id: 1, slots: [], durationMinutes: null }),
        aSection({ id: 2, slots: [aSlot({ id: 2, durationMinutes: 10 })] }),
      ],
    });
    const { marker } = stateAt("2026-08-15T18:04:00Z", session);
    expect(marker).toMatchObject({ rowIndex: 1, fraction: 0.4 });
  });

  it("waits for the start of a session still to come", () => {
    const { wakeAt } = stateAt("2026-08-15T17:59:00Z");
    expect(wakeAt).toBe(new Date("2026-08-15T18:00:00Z").getTime());
  });

  it("waits for the next minute while the session is running", () => {
    const { wakeAt } = stateAt("2026-08-15T18:12:30Z");
    expect(wakeAt).toBe(new Date("2026-08-15T18:13:00Z").getTime());
  });

  it("waits for nothing once the session has run its agenda out", () => {
    expect(stateAt("2026-08-15T18:35:00Z").wakeAt).toBeNull();
  });

  it("waits for nothing on a session that can never show a marker", () => {
    const session = threeBlocks({ hasTime: false });
    expect(stateAt("2026-08-15T17:59:00Z", session).wakeAt).toBeNull();
  });
});
