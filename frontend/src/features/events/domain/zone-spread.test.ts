import { describe, expect, it } from "vitest";

import {
  projectZones,
  spreadSummary,
} from "@/features/events/domain/zone-spread";

/** Thursday 1 October 2026, 16:00 UTC, running 90 minutes. */
const THURSDAY_16_UTC = new Date("2026-10-01T16:00:00Z");

const community = [
  { zone: "Asia/Kolkata", count: 4 },
  { zone: "America/Los_Angeles", count: 2 },
  { zone: "Europe/Berlin", count: 7 },
];

describe("projectZones", () => {
  it("reads one instant in each zone, earliest local hour first", () => {
    const rows = projectZones(community, THURSDAY_16_UTC, 90, "Europe/Berlin");

    expect(
      rows.map((row) => [row.city, row.localStart, row.localEnd, row.count]),
    ).toEqual([
      ["Los Angeles", "09:00", "10:30", 2],
      ["Berlin", "18:00", "19:30", 7],
      ["Kolkata", "21:30", "23:00", 4],
    ]);
  });

  it("names the weekday only where it is not the session's own", () => {
    const rows = projectZones(
      [...community, { zone: "Pacific/Auckland", count: 1 }],
      THURSDAY_16_UTC,
      90,
      "Europe/Berlin",
    );

    expect(rows.map((row) => [row.city, row.day])).toEqual([
      ["Los Angeles", null],
      ["Berlin", null],
      ["Kolkata", null],
      ["Auckland", "Fri"],
    ]);
  });

  it("takes the day from the session's zone, not from the earliest row", () => {
    // Typed in Auckland, where the session is on Friday: now it is Thursday that reads as elsewhere.
    const rows = projectZones(
      [...community, { zone: "Pacific/Auckland", count: 1 }],
      THURSDAY_16_UTC,
      90,
      "Pacific/Auckland",
    );

    expect(rows.map((row) => [row.city, row.day])).toEqual([
      ["Los Angeles", "Thu"],
      ["Berlin", "Thu"],
      ["Kolkata", "Thu"],
      ["Auckland", null],
    ]);
  });

  it("places the bar where the hour falls in the local day", () => {
    const rows = projectZones(community, THURSDAY_16_UTC, 90, "Europe/Berlin");

    expect(rows[0]?.offsetPct).toBeCloseTo((9 / 24) * 100);
    expect(rows[2]?.offsetPct).toBeCloseTo((21.5 / 24) * 100);
    expect(rows[0]?.widthPct).toBeCloseTo((1.5 / 24) * 100);
  });

  it("reads midnight as 00:00 rather than as 24:00, and puts the bar at the day's head", () => {
    const rows = projectZones(
      [{ zone: "Asia/Tokyo", count: 1 }, ...community],
      new Date("2026-10-01T15:00:00Z"),
      60,
      "Europe/Berlin",
    );
    const tokyo = rows.at(-1);

    expect(tokyo?.city).toBe("Tokyo");
    expect(tokyo?.localStart).toBe("00:00");
    expect(tokyo?.day).toBe("Fri");
    expect(tokyo?.offsetPct).toBe(0);
  });

  it("yields nothing for a date the form has not resolved yet", () => {
    expect(projectZones(community, new Date("nonsense"), 90, "UTC")).toEqual(
      [],
    );
  });
});

describe("spreadSummary", () => {
  it("names both ends and how many zones lie between them", () => {
    const rows = projectZones(community, THURSDAY_16_UTC, 90, "Europe/Berlin");

    expect(spreadSummary(rows)).toBe(
      "09:00 in Los Angeles to 23:00 in Kolkata across 3 zones",
    );
  });

  it("drops the count at two, where naming both ends has already given it", () => {
    const rows = projectZones(
      [
        { zone: "America/Los_Angeles", count: 2 },
        { zone: "Europe/Berlin", count: 7 },
      ],
      THURSDAY_16_UTC,
      90,
      "Europe/Berlin",
    );

    expect(spreadSummary(rows)).toBe("09:00 in Los Angeles to 19:30 in Berlin");
  });

  it("states the day on an end that falls on another one", () => {
    const rows = projectZones(
      [...community, { zone: "Pacific/Auckland", count: 1 }],
      THURSDAY_16_UTC,
      90,
      "Europe/Berlin",
    );

    expect(spreadSummary(rows)).toBe(
      "09:00 in Los Angeles to 06:30 Fri in Auckland across 4 zones",
    );
  });

  it("says nothing about a community that reads one clock", () => {
    const rows = projectZones(
      [{ zone: "Europe/Berlin", count: 7 }],
      THURSDAY_16_UTC,
      90,
      "Europe/Berlin",
    );

    expect(spreadSummary(rows)).toBeNull();
  });

  it("says nothing about a community nobody has measured", () => {
    expect(spreadSummary([])).toBeNull();
  });
});
