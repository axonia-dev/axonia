import { describe, expect, it } from "vitest";

import {
  instantToZoned,
  zonedToInstant,
} from "@/features/events/domain/zoned-instant";

describe("zonedToInstant", () => {
  it("reads a wall-clock time in the named zone, not in the viewer's", () => {
    const at = zonedToInstant(
      { date: "2026-08-13", time: "17:00" },
      "Europe/Paris",
    );

    expect(at?.toISOString()).toBe("2026-08-13T15:00:00.000Z");
  });

  it("is exact on a spring-forward day", () => {
    const at = zonedToInstant(
      { date: "2026-03-08", time: "10:00" },
      "America/Los_Angeles",
    );

    expect(at?.toISOString()).toBe("2026-03-08T17:00:00.000Z");
  });

  it("is exact on an autumn-back day", () => {
    const at = zonedToInstant(
      { date: "2026-11-01", time: "10:00" },
      "America/Los_Angeles",
    );

    expect(at?.toISOString()).toBe("2026-11-01T18:00:00.000Z");
  });

  it("resolves an hour a single-pass correction places an hour late, on the spring-forward day", () => {
    // Los Angeles moves 02:00 PST -> 03:00 PDT at 10:00 UTC on 2026-03-08. The UTC guess for 03:00
    // still sits in PST, so one correction lands at 11:00Z, which reads back as 04:00 local.
    const at = zonedToInstant(
      { date: "2026-03-08", time: "03:00" },
      "America/Los_Angeles",
    );

    expect(at?.toISOString()).toBe("2026-03-08T10:00:00.000Z");
  });

  it("resolves an hour a single-pass correction places an hour early, on the autumn-back day", () => {
    // Los Angeles moves 03:00 PDT -> 02:00 PST at 09:00 UTC on 2026-11-01. One correction lands at
    // 10:00Z, which reads back as 02:00 local.
    const at = zonedToInstant(
      { date: "2026-11-01", time: "03:00" },
      "America/Los_Angeles",
    );

    expect(at?.toISOString()).toBe("2026-11-01T11:00:00.000Z");
  });

  it("corrects across a transition in a zone that is not the viewer's", () => {
    // Paris moves 02:00 CET -> 03:00 CEST at 01:00 UTC on 2026-03-29, and 01:30 sits on the CET
    // side of it. One correction lands on 2026-03-28T23:30Z, a different day and hour.
    const at = zonedToInstant(
      { date: "2026-03-29", time: "01:30" },
      "Europe/Paris",
    );

    expect(at?.toISOString()).toBe("2026-03-29T00:30:00.000Z");
  });

  it("treats an empty time as midnight in the zone", () => {
    const at = zonedToInstant({ date: "2026-08-13", time: "" }, "Europe/Paris");

    expect(at?.toISOString()).toBe("2026-08-12T22:00:00.000Z");
  });

  it("returns null for an unparseable date rather than an Invalid Date", () => {
    expect(
      zonedToInstant({ date: "nonsense", time: "17:00" }, "UTC"),
    ).toBeNull();
  });

  it("returns null for an unparseable time rather than an Invalid Date", () => {
    expect(
      zonedToInstant({ date: "2026-08-13", time: "half past" }, "UTC"),
    ).toBeNull();
  });
});

describe("instantToZoned", () => {
  it("round-trips through the same zone", () => {
    const parts = instantToZoned("2026-08-13T15:00:00Z", "Europe/Paris");

    expect(parts).toEqual({ date: "2026-08-13", time: "17:00" });
  });

  it("reports midnight as 00:00 rather than as 24:00", () => {
    const parts = instantToZoned("2026-08-12T22:00:00Z", "Europe/Paris");

    expect(parts).toEqual({ date: "2026-08-13", time: "00:00" });
  });
});
