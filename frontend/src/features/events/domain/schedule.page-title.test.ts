import { describe, expect, it } from "vitest";

import {
  pageTitleOf,
  resolveLabel,
  type ScheduleEntry,
} from "@/features/events/domain/schedule";
import type { Slot } from "@/features/events/domain/slot";
import { aScheduleEntry, aSlot } from "@/test/session-fixture";

const seriesList = [{ id: 1, title: "Friday Tech Talks" }] as never;

function withSlots(slots: Slot[]): ScheduleEntry["sections"] {
  return [
    {
      id: 1,
      sessionId: 7,
      position: 0,
      sectionTypeId: null,
      sectionType: null,
      sectionTypeIcon: null,
      sectionTypeAccentColor: null,
      title: null,
      durationMinutes: null,
      slots,
    },
  ];
}

describe("pageTitleOf", () => {
  it("heads an entry with its first titled talk, as the session page does", () => {
    // given
    const entry = aScheduleEntry({
      title: "Kickoff",
      sections: withSlots([
        aSlot({ title: null }),
        aSlot({ id: 2, title: "Virtual threads deep dive" }),
      ]),
    });

    // then
    expect(pageTitleOf(entry)).toBe("Virtual threads deep dive");
    expect(resolveLabel(entry, seriesList)).not.toBe(pageTitleOf(entry));
  });

  it("falls back to the entry's own title, then to the generic label", () => {
    // given
    const titled = aScheduleEntry({ title: "Kickoff" });
    const untitled = aScheduleEntry({
      sections: withSlots([aSlot({ title: " " })]),
    });

    // then
    expect(pageTitleOf(titled)).toBe("Kickoff");
    expect(resolveLabel(titled, seriesList)).toBe(pageTitleOf(titled));
    expect(pageTitleOf(untitled)).toBe("Session");
  });

  it("never reads the series title, which the schedule label falls back to", () => {
    // given
    const entry = aScheduleEntry({ seriesId: 1 });

    // then
    expect(resolveLabel(entry, seriesList)).toBe("Friday Tech Talks");
    expect(pageTitleOf(entry)).toBe("Session");
  });
});
