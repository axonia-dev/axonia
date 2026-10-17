import { describe, expect, it } from "vitest";

import {
  atFraction,
  follow,
  fractionOf,
  mergeTicks,
  monthMarks,
  nearestTick,
  RIBBON_WINDOW_MS,
  ribbonBounds,
  ribbonSchema,
  type RibbonSpan,
  type RibbonTick,
  ribbonYears,
  stepMonth,
  stepTick,
  ticksIn,
  windowAround,
  windowAt,
  yearRange,
} from "@/features/events/domain/ribbon";

const TODAY = "2026-10-16T00:00:00.000Z";
const DAY_MS = 86_400_000;
const ms = (iso: string) => new Date(iso).getTime();
const tick = (at: string): RibbonTick => ({ at, kind: "MEMORY" });
const MONTH = RIBBON_WINDOW_MS / 12;

describe("ribbonBounds", () => {
  it("reaches from the first session to three years ahead", () => {
    expect(ribbonBounds("2024-03-01T18:00:00Z", ms(TODAY))).toEqual({
      fromMs: ms("2024-03-01T18:00:00Z"),
      toMs: ms(TODAY) + 1095 * DAY_MS,
    });
  });

  it("starts at today with no history, or when every session is still ahead", () => {
    expect(ribbonBounds(null, ms(TODAY)).fromMs).toBe(ms(TODAY));
    expect(ribbonBounds("2027-01-01T00:00:00Z", ms(TODAY)).fromMs).toBe(
      ms(TODAY),
    );
  });
});

describe("the rolling window", () => {
  const bounds = ribbonBounds("2024-03-01T00:00:00Z", ms(TODAY));

  it("is a year wide wherever it starts", () => {
    const span = windowAt(ms("2025-02-01T00:00:00Z"), bounds);
    expect(span.toMs - span.fromMs).toBe(RIBBON_WINDOW_MS);
  });

  it("slides back inside the bounds at either end", () => {
    expect(windowAt(0, bounds).fromMs).toBe(bounds.fromMs);
    expect(windowAt(bounds.toMs, bounds).toMs).toBe(bounds.toMs);
  });

  it("opens centred on now when history allows it", () => {
    const span = windowAround(ms(TODAY), bounds);
    expect(span.fromMs).toBe(ms(TODAY) - RIBBON_WINDOW_MS / 2);
  });

  it("opens at the first session when it is younger than half a window", () => {
    const young = ribbonBounds("2026-09-10T00:00:00Z", ms(TODAY));
    expect(windowAround(ms(TODAY), young).fromMs).toBe(
      ms("2026-09-10T00:00:00Z"),
    );
  });

  it("never grows wider than the bounds", () => {
    const narrow = { fromMs: 0, toMs: MONTH };
    expect(windowAt(5, narrow)).toEqual(narrow);
  });
});

describe("follow", () => {
  const bounds = ribbonBounds("2024-03-01T00:00:00Z", ms(TODAY));
  const span = windowAround(ms(TODAY), bounds);

  it("leaves the window alone while the lens stays a margin inside it", () => {
    expect(follow(span, ms(TODAY), 1, bounds)).toBe(span);
  });

  it("rolls forward to keep a lens heading for the end a month inside it", () => {
    const at = span.toMs - MONTH / 2;
    expect(follow(span, at, 1, bounds).toMs).toBe(at + MONTH);
  });

  it("rolls back to keep a lens heading for the start a month inside it", () => {
    const at = span.fromMs + MONTH / 2;
    expect(follow(span, at, -1, bounds).fromMs).toBe(at - MONTH);
  });

  it("never drags the window after a lens heading away from its edge", () => {
    expect(follow(span, span.toMs - MONTH / 2, -1, bounds)).toBe(span);
    expect(follow(span, span.fromMs - MONTH, 1, bounds)).toBe(span);
  });
});

describe("ribbonYears", () => {
  it("covers every year the window touches, and a quarter beyond", () => {
    expect(
      ribbonYears({
        fromMs: ms("2026-04-16T00:00:00Z"),
        toMs: ms("2027-04-16T00:00:00Z"),
      }),
    ).toEqual([2026, 2027]);
    expect(
      ribbonYears({
        fromMs: ms("2026-02-01T00:00:00Z"),
        toMs: ms("2026-11-01T00:00:00Z"),
      }),
    ).toEqual([2025, 2026, 2027]);
  });

  it("spans a whole UTC year per bucket", () => {
    expect(yearRange(2027)).toEqual({
      fromIso: "2027-01-01T00:00:00.000Z",
      toIso: "2028-01-01T00:00:00.000Z",
    });
  });
});

describe("mergeTicks", () => {
  it("orders the years' ticks and keeps a tick on a shared edge once", () => {
    const edge = tick("2027-01-01T00:00:00Z");
    const late = tick("2026-12-04T18:00:00Z");
    const early = tick("2027-01-08T18:00:00Z");
    expect(
      mergeTicks([
        [early, edge],
        [late, edge],
      ]),
    ).toEqual([late, edge, early]);
  });
});

describe("positions", () => {
  const span: RibbonSpan = { fromMs: 1000, toMs: 3000 };

  it("clamps fractions and round-trips inside the span", () => {
    expect(fractionOf(0, span)).toBe(0);
    expect(fractionOf(9000, span)).toBe(1);
    expect(atFraction(fractionOf(1500, span), span)).toBe(1500);
  });

  it("is zero for an empty span", () => {
    expect(fractionOf(5, { fromMs: 5, toMs: 5 })).toBe(0);
  });
});

describe("ticks", () => {
  const a = tick("2026-08-01T00:00:00Z");
  const b = tick("2026-09-01T00:00:00Z");
  const c = tick("2026-10-20T00:00:00Z");
  const all = [a, b, c];

  it("keeps the ticks inside the span", () => {
    const span: RibbonSpan = {
      fromMs: ms(b.at),
      toMs: ms(c.at),
    };
    expect(ticksIn(all, span)).toEqual([b, c]);
  });

  it("finds the nearest of two neighbours, or none", () => {
    expect(nearestTick(all, ms("2026-08-10T00:00:00Z"))).toBe(a);
    expect(nearestTick(all, ms("2026-08-25T00:00:00Z"))).toBe(b);
    expect(nearestTick([], 5)).toBeNull();
  });

  it("steps strictly forward and back, null at the ends", () => {
    expect(stepTick(all, ms(a.at), 1)).toBe(b);
    expect(stepTick(all, ms(c.at), -1)).toBe(b);
    expect(stepTick(all, ms(c.at), 1)).toBeNull();
    expect(stepTick(all, ms(a.at), -1)).toBeNull();
  });

  it("steps about a month the way of the key, null at the ends", () => {
    expect(stepMonth(all, ms(a.at), 1)).toBe(b);
    expect(stepMonth(all, ms(b.at), -1)).toBe(a);
    expect(stepMonth(all, ms(c.at), -1)).toBe(b);
    expect(stepMonth([a], ms(a.at), 1)).toBeNull();
    expect(stepMonth(all, ms(c.at), 1)).toBeNull();
    expect(stepMonth(all, ms(a.at), -1)).toBeNull();
    expect(stepMonth([], ms(a.at), 1)).toBeNull();
  });
});

describe("monthMarks", () => {
  it("marks the first of each month inside the span", () => {
    const marks = monthMarks({
      fromMs: ms("2026-10-16T00:00:00Z"),
      toMs: ms("2027-01-14T00:00:00Z"),
    });
    expect(marks.map((m) => m.label)).toEqual(["Nov", "Dec", "Jan"]);
    expect(marks[0]?.atMs).toBe(ms("2026-11-01T00:00:00Z"));
  });

  it("includes a month starting exactly at the span start", () => {
    const marks = monthMarks({
      fromMs: ms("2026-11-01T00:00:00Z"),
      toMs: ms("2026-11-20T00:00:00Z"),
    });
    expect(marks.map((m) => m.label)).toEqual(["Nov"]);
  });
});

describe("ribbonSchema", () => {
  it("parses the wire shape", () => {
    expect(
      ribbonSchema.parse({
        firstSessionAt: null,
        ticks: [{ at: "2026-10-01T00:00:00Z", kind: "PROJECTED" }],
      }).ticks,
    ).toHaveLength(1);
  });
});
