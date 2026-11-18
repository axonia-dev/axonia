import { describe, expect, it } from "vitest";

import type { PlannedMeeting } from "@/features/mentoring/domain/mentoring";
import { journalOf, noteOf } from "@/features/mentoring/test-data";
import { meetingsOf } from "@/features/mentoring/ui/logbook/meetings";
import {
  behindOf,
  latestPlannableDay,
  owedOf,
  plannedTopicsOn,
  upcomingOf,
} from "@/features/mentoring/ui/logbook/plan";

function planned(
  id: number,
  on: string,
  passed: boolean,
  topicIds: number[] = [],
): PlannedMeeting {
  return {
    id,
    on,
    topicIds,
    plannedById: "u-1",
    plannedByName: "Ada",
    passed,
  };
}

const journal = journalOf({
  today: "2026-11-16",
  meetings: [
    planned(1, "2026-11-02", true),
    planned(2, "2026-11-09", true, [4]),
    planned(3, "2026-11-16", false, [7, 8]),
    planned(4, "2026-11-26", false),
  ],
  notes: [noteOf({ meetingOn: "2026-11-05" })],
});

describe("plan", () => {
  it("upcomingOf and owedOf split at today", () => {
    // when
    const upcoming = upcomingOf(journal).map((meeting) => meeting.id);
    const owed = owedOf(journal).map((meeting) => meeting.id);

    // then
    expect(upcoming).toEqual([3, 4]);
    expect(owed).toEqual([2, 1]);
  });

  it("names the topics planned on a day, none when nothing is planned", () => {
    // when
    const today = plannedTopicsOn(journal, "2026-11-16");
    const free = plannedTopicsOn(journal, "2026-11-17");

    // then
    expect(today).toEqual([7, 8]);
    expect(free).toEqual([]);
  });

  it("lays owed and held meetings behind today, newest first", () => {
    // when
    const behind = behindOf(owedOf(journal), meetingsOf(journal));

    // then
    expect(behind.map((entry) => [entry.kind, entry.meeting.on])).toEqual([
      ["owed", "2026-11-09"],
      ["held", "2026-11-05"],
      ["owed", "2026-11-02"],
    ]);
  });

  it("plans at most 366 days ahead", () => {
    // when
    const latest = latestPlannableDay("2026-11-16");

    // then
    expect(latest).toBe("2027-11-17");
  });
});
