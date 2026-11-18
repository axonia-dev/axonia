import { describe, expect, it } from "vitest";

import {
  lobbyEyebrow,
  nextSessionLine,
  sessionName,
} from "@/features/community/ui/lobby/lobby-copy";
import { lobbyItem } from "@/test/lobby-fixture";

const now = new Date("2026-11-17T12:00:00Z");

describe("lobbyEyebrow", () => {
  it("says how many joined when three or more did in 30 days", () => {
    // given
    const item = lobbyItem({ joinedLast30Days: 3, nextSessionThisWeek: true });

    // when
    const actual = lobbyEyebrow(item);

    // then
    expect(actual).toBe("Guild · +3 joined in 30 days");
  });

  it("says a session falls this week when growth is too small to mention", () => {
    // given
    const item = lobbyItem({ joinedLast30Days: 2, nextSessionThisWeek: true });

    // when
    const actual = lobbyEyebrow(item);

    // then
    expect(actual).toBe("Guild · Session this week");
  });

  it("is the kind alone otherwise", () => {
    // when
    const actual = lobbyEyebrow(lobbyItem({ joinedLast30Days: 2 }));

    // then
    expect(actual).toBe("Guild");
  });
});

describe("nextSessionLine", () => {
  it("names the day, the time in the reader's zone and the title", () => {
    // given - the suite runs in America/Los_Angeles, eight hours behind UTC in November
    const item = lobbyItem({
      nextSession: {
        title: "Kafka deep dive",
        scheduledAt: "2026-11-19T19:00:00Z",
        hasTime: true,
        zone: "Europe/Zurich",
      },
    });

    // when
    const actual = nextSessionLine(item, now);

    // then
    expect(actual).toBe("Thu 19 Nov · 11:00 · Kafka deep dive");
  });

  it("leaves out a missing title", () => {
    // given
    const item = lobbyItem({
      nextSession: {
        title: null,
        scheduledAt: "2026-11-19T19:00:00Z",
        hasTime: true,
        zone: "Europe/Zurich",
      },
    });

    // when
    const actual = nextSessionLine(item, now);

    // then
    expect(actual).toBe("Thu 19 Nov · 11:00");
  });

  it("names the year of a session in another year", () => {
    // given
    const item = lobbyItem({
      nextSession: {
        title: null,
        scheduledAt: "2027-01-07T19:00:00Z",
        hasTime: true,
        zone: "Europe/Zurich",
      },
    });

    // when
    const actual = nextSessionLine(item, now);

    // then
    expect(actual).toBe("Thu 7 Jan 2027 · 11:00");
  });

  it.each([
    ["Europe/Zurich", "2026-11-18T23:00:00Z"],
    ["America/New_York", "2026-11-19T05:00:00Z"],
  ])(
    "names only the day of an untimed session, read on the clock of its series in %s",
    (zone, scheduledAt) => {
      // given - midnight in the series' zone, a day apart in UTC and in the reader's zone
      const item = lobbyItem({
        nextSession: {
          title: "Open space",
          scheduledAt,
          hasTime: false,
          zone,
        },
      });

      // when
      const actual = nextSessionLine(item, now);

      // then
      expect(actual).toBe("Thu 19 Nov · Open space");
    },
  );

  it("says nothing is scheduled when there is no next session", () => {
    // when
    const actual = nextSessionLine(lobbyItem(), now);

    // then
    expect(actual).toBe("Nothing scheduled yet");
  });
});

describe("sessionName", () => {
  it.each([
    ["Kafka deep dive", "Kafka deep dive"],
    ["  Kafka  ", "Kafka"],
    ["   ", "A session"],
    [null, "A session"],
  ])("names %j as %j", (title, expected) => {
    // when
    const actual = sessionName({ title });

    // then
    expect(actual).toBe(expected);
  });
});
