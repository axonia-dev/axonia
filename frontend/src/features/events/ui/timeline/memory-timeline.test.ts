import { describe, expect, it } from "vitest";

import type { Memory } from "@/features/events/domain/memory";
import { NO_TRACES } from "@/features/events/domain/memory";
import {
  buildMemoryTimeline,
  mergeMemories,
  splitEnded,
} from "@/features/events/ui/timeline/memory-timeline";
import { aScheduleEntry } from "@/test/session-fixture";

const NOW = new Date("2026-10-16T12:00:00Z");

function on(sessionId: number, scheduledAt: string) {
  return aScheduleEntry({
    sessionId,
    scheduledAt,
    occurrenceDate: scheduledAt.slice(0, 10),
    seriesTimeZone: "UTC",
    status: "HELD",
  });
}

function memoryOn(sessionId: number, scheduledAt: string): Memory {
  return { entry: on(sessionId, scheduledAt), traces: NO_TRACES };
}

describe("splitEnded", () => {
  it("keeps untimed and future entries ahead and moves an ended timed one out", () => {
    // given
    const endedOne = on(1, "2026-10-16T09:00:00Z");
    const later = on(2, "2026-10-16T18:00:00Z");
    const untimed = { ...on(3, "2026-10-15T00:00:00Z"), hasTime: false };
    // when
    const { ended, ahead } = splitEnded([endedOne, later, untimed], NOW);
    // then
    expect(ended).toEqual([endedOne]);
    expect(ahead).toEqual([later, untimed]);
  });
});

describe("mergeMemories", () => {
  it("returns newest first", () => {
    // when
    const memories = mergeMemories(
      [],
      [
        {
          items: [
            memoryOn(1, "2026-09-01T18:00:00Z"),
            memoryOn(2, "2026-10-01T18:00:00Z"),
          ],
          next: null,
        },
      ],
    );
    // then
    expect(memories.map((m) => m.entry.sessionId)).toEqual([2, 1]);
  });

  it("keeps one copy of a session ended locally and also served, with the server traces", () => {
    // given
    const local = on(5, "2026-10-16T09:00:00Z");
    const served: Memory = {
      entry: local,
      traces: { loves: 4, recorded: true, artifacts: 1 },
    };
    // when
    const memories = mergeMemories([local], [{ items: [served], next: null }]);
    // then
    expect(memories).toEqual([served]);
  });

  it("turns a local ended session into a memory with no traces yet", () => {
    // given
    const local = on(5, "2026-10-16T09:00:00Z");
    // when
    const memories = mergeMemories([local], []);
    // then
    expect(memories).toEqual([{ entry: local, traces: NO_TRACES }]);
  });

  it("leaves out a projected occurrence that passed unopened", () => {
    // given
    const ghost = { ...on(5, "2026-10-16T09:00:00Z"), sessionId: null };
    // when
    const memories = mergeMemories([ghost], []);
    // then
    expect(memories).toEqual([]);
  });
});

describe("buildMemoryTimeline", () => {
  it("orders oldest first, labelling each month once and marking a quiet stretch", () => {
    // given
    const memories = [
      memoryOn(3, "2026-10-02T18:00:00Z"),
      memoryOn(2, "2026-09-11T18:00:00Z"),
      memoryOn(1, "2026-09-04T18:00:00Z"),
    ];
    // when
    const items = buildMemoryTimeline(memories, new Set([3]));
    // then
    expect(items).toEqual([
      { kind: "month", label: "September" },
      { kind: "memory", memory: memories[2], arriving: false },
      { kind: "memory", memory: memories[1], arriving: false },
      { kind: "gap", weeks: 3 },
      { kind: "month", label: "October" },
      { kind: "memory", memory: memories[0], arriving: true },
    ]);
  });

  it("skips a memory carrying no day", () => {
    // given
    const undated: Memory = {
      entry: { ...on(1, "x"), scheduledAt: null, occurrenceDate: null },
      traces: NO_TRACES,
    };
    // when
    const items = buildMemoryTimeline([undated], new Set());
    // then
    expect(items).toEqual([]);
  });
});
