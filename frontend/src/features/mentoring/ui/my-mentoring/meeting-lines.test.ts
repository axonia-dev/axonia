import { describe, expect, it } from "vitest";

import type { PairingSummary } from "@/features/mentoring/domain/mentoring";
import { pairingOf } from "@/features/mentoring/test-data";
import {
  daysBetween,
  lapseLine,
  nextMeetingLine,
} from "@/features/mentoring/ui/my-mentoring/meeting-lines";

const TODAY = "2026-11-17";

const meetingOn = (on: string, overrides: Partial<PairingSummary> = {}) =>
  pairingOf({ nextMeeting: { on, passed: false }, notes: 2, ...overrides });

describe("daysBetween", () => {
  it("counts calendar days, across a month end and backwards", () => {
    // when
    const actual = [
      daysBetween(TODAY, TODAY),
      daysBetween(TODAY, "2026-12-02"),
      daysBetween(TODAY, "2026-11-12"),
    ];

    // then
    expect(actual).toEqual([0, 15, -5]);
  });
});

describe("nextMeetingLine", () => {
  it.each([
    ["today", meetingOn(TODAY), "Today", "next meeting"],
    ["tomorrow", meetingOn("2026-11-18"), "Wed 18", "next meeting · tomorrow"],
    ["later", meetingOn("2026-11-19"), "Thu 19", "next meeting · in 2 days"],
    [
      "a first meeting today",
      meetingOn(TODAY, { notes: 0 }),
      "Today",
      "first meeting",
    ],
    [
      "a first meeting tomorrow",
      meetingOn("2026-11-18", { notes: 0 }),
      "Wed 18",
      "first meeting · tomorrow",
    ],
    [
      "a first meeting later",
      meetingOn("2026-11-24", { notes: 0 }),
      "Tue 24",
      "first meeting · in 7 days",
    ],
  ])("announces a meeting %s", (_name, pairing, big, small) => {
    // when
    const actual = nextMeetingLine(pairing, TODAY);

    // then
    expect(actual).toEqual({ big, small, tone: "plain" });
  });

  it("theStripLineAddsLaterPlanned", () => {
    // given
    const pairing = meetingOn("2026-11-25", { laterPlanned: 2 });

    // when
    const actual = nextMeetingLine(pairing, TODAY);

    // then
    expect(actual).toEqual({
      big: "Wed 25",
      small: "next meeting · in 8 days · 2 more planned",
      tone: "plain",
    });
  });

  it("anOwedMeetingStillLeadsMyMentoring", () => {
    // given
    const pairing = pairingOf({
      nextMeeting: { on: "2026-11-12", passed: true },
      laterPlanned: 1,
      notes: 3,
    });

    // when
    const actual = nextMeetingLine(pairing, TODAY);

    // then
    expect(actual).toEqual({
      big: "Thu 12",
      small: "notes to write · 1 more planned",
      tone: "warning",
    });
  });

  it("asks for the notes of a meeting that has passed, in the warning ink", () => {
    // given
    const pairing = pairingOf({
      nextMeeting: { on: "2026-11-12", passed: true },
      notes: 3,
    });

    // when
    const actual = nextMeetingLine(pairing, TODAY);

    // then
    expect(actual).toEqual({
      big: "Thu 12",
      small: "notes to write",
      tone: "warning",
    });
  });

  it("says when the pair last met when no date is set", () => {
    // given
    const pairing = pairingOf({ lastMetOn: "2026-11-09", notes: 1 });

    // when
    const actual = nextMeetingLine(pairing, TODAY);

    // then
    expect(actual).toEqual({
      big: "No date set",
      small: "last met 9 Nov",
      tone: "warning",
    });
  });

  it("says the pair has not met yet when no date is set and no meeting held", () => {
    // when
    const actual = nextMeetingLine(pairingOf(), TODAY);

    // then
    expect(actual).toEqual({
      big: "No date set",
      small: "no meeting yet",
      tone: "warning",
    });
  });
});

describe("lapseLine", () => {
  it.each([
    [TODAY, "lapses today"],
    ["2026-11-18", "1 day left"],
    ["2026-12-12", "25 days left"],
  ])("reads a proposal lapsing on %s as %s", (lapsesOn, expected) => {
    // when
    const actual = lapseLine(lapsesOn, TODAY);

    // then
    expect(actual).toBe(expected);
  });
});
