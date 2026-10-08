import { describe, expect, it } from "vitest";

import {
  runSheet,
  sectionAnchorId,
  sectionRunMinutes,
} from "@/features/events/domain/run-sheet";
import { aScheduledSession, aSection, aSlot } from "@/test/session-fixture";

/** 18:00 UTC is 20:00 in Europe/Paris, which the fixture's series is read in. */
const AT_SIX_UTC = "2026-08-15T18:00:00Z";

describe("sectionRunMinutes", () => {
  it("times a section by the talks actually on it", () => {
    const section = aSection({
      durationMinutes: 30,
      slots: [
        aSlot({ durationMinutes: 12 }),
        aSlot({ id: 8, durationMinutes: 8 }),
      ],
    });
    expect(sectionRunMinutes(section)).toBe(20);
  });

  it("times a round table by its budget, because it legitimately holds no talks", () => {
    const section = aSection({ durationMinutes: 25, slots: [] });
    expect(sectionRunMinutes(section)).toBe(25);
  });

  it("gives a round table with no budget no length at all", () => {
    const section = aSection({ durationMinutes: null, slots: [] });
    expect(sectionRunMinutes(section)).toBe(0);
  });
});

describe("sectionAnchorId", () => {
  it("names a section's anchor from its id", () => {
    expect(sectionAnchorId(3)).toBe("run-sheet-section-3");
  });
});

describe("runSheet", () => {
  it("accumulates each section's start from the ones before it", () => {
    const session = aScheduledSession({
      scheduledAt: AT_SIX_UTC,
      durationMinutes: 60,
      sections: [
        aSection({ id: 1, slots: [aSlot({ id: 1, durationMinutes: 10 })] }),
        aSection({ id: 2, slots: [aSlot({ id: 2, durationMinutes: 15 })] }),
        aSection({ id: 3, slots: [aSlot({ id: 3, durationMinutes: 20 })] }),
      ],
    });
    expect(runSheet(session).rows.map((r) => r.startMinutes)).toEqual([
      0, 10, 25,
    ]);
  });

  it("labels each start on the clock the community meets on, not the reader's", () => {
    const session = aScheduledSession({
      scheduledAt: AT_SIX_UTC,
      seriesTimeZone: "Europe/Paris",
      sections: [
        aSection({ id: 1, slots: [aSlot({ id: 1, durationMinutes: 10 })] }),
        aSection({ id: 2, slots: [aSlot({ id: 2, durationMinutes: 15 })] }),
      ],
    });
    expect(runSheet(session).rows.map((r) => r.startLabel)).toEqual([
      "20:00",
      "20:10",
    ]);
  });

  it("reads an untimed occurrence in offsets, because nobody chose it an hour", () => {
    const session = aScheduledSession({
      scheduledAt: AT_SIX_UTC,
      hasTime: false,
      durationMinutes: 85,
      sections: [
        aSection({ id: 1, slots: [aSlot({ id: 1, durationMinutes: 10 })] }),
        aSection({ id: 2, slots: [aSlot({ id: 2, durationMinutes: 75 })] }),
      ],
    });
    expect(runSheet(session).rows.map((r) => r.startLabel)).toEqual([
      "+0:00",
      "+0:10",
    ]);
    expect(runSheet(session).plannedEndLabel).toBe("+1:25");
  });

  it("draws no marker when no budget was ever set", () => {
    const session = aScheduledSession({
      durationMinutes: null,
      sections: [aSection({ id: 1, slots: [aSlot({ durationMinutes: 10 })] })],
    });
    const sheet = runSheet(session);
    expect(sheet.plannedEndAfter).toBeNull();
    expect(sheet.plannedEndLabel).toBeNull();
    expect(sheet.overrunMinutes).toBe(0);
  });

  it("puts the marker after every section when the whole session fits", () => {
    const session = aScheduledSession({
      durationMinutes: 60,
      sections: [
        aSection({ id: 1, slots: [aSlot({ id: 1, durationMinutes: 10 })] }),
        aSection({ id: 2, slots: [aSlot({ id: 2, durationMinutes: 15 })] }),
      ],
    });
    const sheet = runSheet(session);
    expect(sheet.plannedEndAfter).toBe(2);
    expect(sheet.overrunMinutes).toBe(0);
    expect(sheet.rows.every((r) => !r.pastPlannedEnd)).toBe(true);
  });

  it("puts the marker before the first section that starts past the budget", () => {
    const session = aScheduledSession({
      durationMinutes: 20,
      sections: [
        aSection({ id: 1, slots: [aSlot({ id: 1, durationMinutes: 10 })] }),
        aSection({ id: 2, slots: [aSlot({ id: 2, durationMinutes: 15 })] }),
        aSection({ id: 3, slots: [aSlot({ id: 3, durationMinutes: 10 })] }),
      ],
    });
    const sheet = runSheet(session);
    expect(sheet.plannedEndAfter).toBe(2);
    expect(sheet.rows.map((r) => r.pastPlannedEnd)).toEqual([
      false,
      false,
      true,
    ]);
    expect(sheet.overrunMinutes).toBe(15);
  });

  it("never splits a section, so a block that merely runs over is not marked past the end", () => {
    const session = aScheduledSession({
      durationMinutes: 20,
      sections: [
        aSection({ id: 1, slots: [aSlot({ id: 1, durationMinutes: 10 })] }),
        aSection({ id: 2, slots: [aSlot({ id: 2, durationMinutes: 30 })] }),
      ],
    });
    const sheet = runSheet(session);
    expect(sheet.plannedEndAfter).toBe(2);
    expect(sheet.rows.map((r) => r.pastPlannedEnd)).toEqual([false, false]);
    expect(sheet.overrunMinutes).toBe(20);
  });

  it("marks a section that starts exactly at the budget as past the planned end", () => {
    const session = aScheduledSession({
      durationMinutes: 10,
      sections: [
        aSection({ id: 1, slots: [aSlot({ id: 1, durationMinutes: 10 })] }),
        aSection({ id: 2, slots: [aSlot({ id: 2, durationMinutes: 5 })] }),
      ],
    });
    const sheet = runSheet(session);
    expect(sheet.plannedEndAfter).toBe(1);
    expect(sheet.rows.map((r) => r.pastPlannedEnd)).toEqual([false, true]);
  });

  it("holds an untimed occurrence to its budget too, because nobody choosing an hour does not mean nobody set a length", () => {
    const session = aScheduledSession({
      hasTime: false,
      durationMinutes: 20,
      sections: [
        aSection({ id: 1, slots: [aSlot({ id: 1, durationMinutes: 10 })] }),
        aSection({ id: 2, slots: [aSlot({ id: 2, durationMinutes: 25 })] }),
      ],
    });
    const sheet = runSheet(session);
    expect(sheet.plannedEndLabel).toBe("+0:20");
    expect(sheet.overrunMinutes).toBe(15);
  });

  it("has no rows and no marker for a session nobody has shaped", () => {
    const sheet = runSheet(aScheduledSession({ sections: [] }));
    expect(sheet.rows).toEqual([]);
    expect(sheet.plannedEndAfter).toBe(0);
  });
});
