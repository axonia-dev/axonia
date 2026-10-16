import { describe, expect, it } from "vitest";

import {
  atFraction,
  fractionOf,
  monthMarks,
  nearestTick,
  ribbonSchema,
  type RibbonSpan,
  ribbonSpan,
  type RibbonTick,
  ribbonWindow,
  stepMonth,
  stepTick,
  ticksIn,
} from "@/features/events/domain/ribbon";

const TODAY = "2026-10-16T00:00:00.000Z";
const END = "2027-01-14T00:00:00.000Z";
const ms = (iso: string) => new Date(iso).getTime();
const tick = (at: string): RibbonTick => ({ at, kind: "MEMORY" });

describe("ribbonWindow", () => {
  it("covers a year back up to the future end", () => {
    expect(ribbonWindow(TODAY, END, 0)).toEqual({
      fromIso: "2025-10-16T00:00:00.000Z",
      toIso: END,
      yearsBack: 0,
    });
  });

  it("slides back a year at a time", () => {
    expect(ribbonWindow(TODAY, END, 1)).toEqual({
      fromIso: "2024-10-16T00:00:00.000Z",
      toIso: "2025-10-16T00:00:00.000Z",
      yearsBack: 1,
    });
  });
});

describe("ribbonSpan", () => {
  const window = ribbonWindow(TODAY, END, 0);

  it("starts at the first session when it is inside the window", () => {
    expect(ribbonSpan(window, "2026-03-01T00:00:00Z", TODAY)).toEqual({
      fromMs: ms("2026-03-01T00:00:00Z"),
      toMs: ms(END),
      hasOlder: false,
    });
  });

  it("starts at today with no history", () => {
    expect(ribbonSpan(window, null, TODAY).fromMs).toBe(ms(TODAY));
  });

  it("starts at today, never later, when every session is still ahead", () => {
    // given a first session after today
    const span = ribbonSpan(window, END, TODAY);
    // then
    expect(span).toEqual({ fromMs: ms(TODAY), toMs: ms(END), hasOlder: false });
  });

  it("caps at the window and offers older history", () => {
    const span = ribbonSpan(window, "2023-01-01T00:00:00Z", TODAY);
    expect(span.fromMs).toBe(ms(window.fromIso));
    expect(span.hasOlder).toBe(true);
  });

  it("starts at the window start once slid back", () => {
    const older = ribbonWindow(TODAY, END, 1);
    const span = ribbonSpan(older, "2025-01-01T00:00:00Z", TODAY);
    expect(span).toEqual({
      fromMs: ms(older.fromIso),
      toMs: ms(older.toIso),
      hasOlder: false,
    });
  });
});

describe("positions", () => {
  const span: RibbonSpan = { fromMs: 1000, toMs: 3000, hasOlder: false };

  it("clamps fractions and round-trips inside the span", () => {
    expect(fractionOf(0, span)).toBe(0);
    expect(fractionOf(9000, span)).toBe(1);
    expect(atFraction(fractionOf(1500, span), span)).toBe(1500);
  });

  it("is zero for an empty span", () => {
    expect(fractionOf(5, { fromMs: 5, toMs: 5, hasOlder: false })).toBe(0);
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
      hasOlder: false,
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

  it("steps about a month, never staying on its own tick", () => {
    expect(stepMonth(all, ms(a.at), 1)).toBe(b);
    expect(stepMonth(all, ms(b.at), -1)).toBe(a);
    expect(stepMonth(all, ms(c.at), -1)).toBe(b);
    expect(stepMonth([a], ms(a.at), 1)).toBe(a);
    expect(stepMonth([], ms(a.at), 1)).toBeNull();
  });
});

describe("monthMarks", () => {
  it("marks the first of each month inside the span", () => {
    const marks = monthMarks({
      fromMs: ms("2026-10-16T00:00:00Z"),
      toMs: ms("2027-01-14T00:00:00Z"),
      hasOlder: false,
    });
    expect(marks.map((m) => m.label)).toEqual(["Nov", "Dec", "Jan"]);
    expect(marks[0]?.atMs).toBe(ms("2026-11-01T00:00:00Z"));
  });

  it("includes a month starting exactly at the span start", () => {
    const marks = monthMarks({
      fromMs: ms("2026-11-01T00:00:00Z"),
      toMs: ms("2026-11-20T00:00:00Z"),
      hasOlder: false,
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
