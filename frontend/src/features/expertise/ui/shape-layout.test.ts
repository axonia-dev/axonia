import { describe, expect, it } from "vitest";

import type { MeasureText } from "@/features/expertise/ui/name-style";
import {
  axisAngle,
  type LabelSpec,
  layoutShape,
  LINE_HEIGHT,
  MARK_WIDTH,
  type PlacedLabel,
} from "@/features/expertise/ui/shape-layout";

const EPSILON = 1e-6;
const eight = (text: string) => text.length * 8;
const measuredLike: MeasureText = (text, style) =>
  text.length *
  style.fontSize *
  (style.tracking > 0 ? 0.55 + style.tracking : 0.5);

function label(text: string, over: Partial<LabelSpec> = {}): LabelSpec {
  return { text, kind: "neighbourhood", marks: 0, reached: false, ...over };
}

const SEVEN = [
  label("AROUND KAFKA", { reached: true }),
  label("Observability", { kind: "named", reached: true }),
  label("AROUND KUBERNETES", { marks: 1, reached: true }),
  label("AROUND POSTGRES", { marks: 1 }),
  label("AROUND REACT"),
  label("Security", { kind: "named", marks: 1 }),
  label("AROUND JAVA", { reached: true }),
];

const LONG = [
  label("AROUND KAFKA"),
  label("infrastructure-as-code", { kind: "area", marks: 2, reached: true }),
  label("Developer Experience and Platform Tooling Guild", {
    kind: "named",
    marks: 1,
    reached: true,
  }),
  label("AROUND POSTGRES", { marks: 1 }),
  label("AROUND REACT"),
  label("AROUND THREAT-MODELING", { marks: 1, reached: true }),
  label("AROUND JAVA", { reached: true }),
];

function rotations(specs: readonly LabelSpec[]): LabelSpec[][] {
  return specs.map((_, r) => [...specs.slice(r), ...specs.slice(0, r)]);
}

function expectInside(layout: ReturnType<typeof layoutShape>) {
  for (const l of layout.labels.filter((x) => x.visible)) {
    const left = l.left + layout.width / 2;
    expect(left).toBeGreaterThanOrEqual(-EPSILON);
    expect(left + l.width).toBeLessThanOrEqual(layout.width + EPSILON);
    expect(l.top + layout.centreY).toBeGreaterThanOrEqual(-EPSILON);
    expect(l.top + l.height + layout.centreY).toBeLessThanOrEqual(
      layout.height + EPSILON,
    );
  }
}

describe("axisAngle", () => {
  it("starts at the top and goes clockwise", () => {
    expect(axisAngle(0, 4)).toBeCloseTo(-Math.PI / 2);
    expect(axisAngle(1, 4)).toBeCloseTo(0);
  });
});

describe("layoutShape", () => {
  it("lays out by the widths it is handed where text can be measured", () => {
    const layout = layoutShape(
      [label("AROUND KAFKA", { marks: 1 })],
      704,
      (text) => text.length * 5,
    );
    expect(layout.labels[0]!.width).toBe(60 + MARK_WIDTH);
  });

  it("keeps every name inside the width on a desktop card", () => {
    const layout = layoutShape(SEVEN, 704);
    expectInside(layout);
    expect(layout.labels.every((l) => l.visible)).toBe(true);
    expect(layout.radius).toBeLessThanOrEqual(150);
  });

  it("wraps and shrinks the drawing at phone width instead of clipping a name", () => {
    const layout = layoutShape(SEVEN, 320);
    expect(layout.labels[2]!.lines).toEqual(["AROUND", "KUBERNETES"]);
    expectInside(layout);
    expect(layout.radius).toBeGreaterThanOrEqual(40);
  });

  it.each([311, 343])(
    "keeps long reached names inside a %ipx drawing on every spoke",
    (width) => {
      for (const specs of rotations(LONG)) {
        const layout = layoutShape(specs, width);
        expectInside(layout);
        specs.forEach((spec, i) => {
          if (spec.reached) expect(layout.labels.at(i)!.visible).toBe(true);
        });
      }
    },
  );

  it("fits the whole drawing and every name in its height", () => {
    const layout = layoutShape(SEVEN, 704);
    expectInside(layout);
    expect(layout.centreY - layout.radius).toBeGreaterThanOrEqual(0);
    expect(layout.centreY + layout.radius).toBeLessThanOrEqual(layout.height);
  });

  it("keeps an unreached name that no line can hold whole, cut short, when nothing collides with it", () => {
    const layout = layoutShape(
      [
        label("AROUND KAFKA", { reached: true }),
        label("Developer Experience and Platform Tooling Guild", {
          kind: "named",
        }),
        label("AROUND JAVA", { reached: true }),
      ],
      311,
    );
    expect(layout.labels[1]!.visible).toBe(true);
    expect(layout.labels[1]!.lines.at(-1)).toMatch(/…$/u);
    expect(layout.labels.every((l) => l.visible)).toBe(true);
  });
});

describe("where the drawing puts the text and the marks", () => {
  function only(spec: LabelSpec, index: number): PlacedLabel {
    const specs = [0, 1, 2, 3].map((i) =>
      i === index ? spec : label(`AROUND X${i}`),
    );
    return layoutShape(specs, 704, eight).labels.at(index)!;
  }

  const MARKED = label("AROUND THREAT-MODELING", { marks: 2, reached: true });

  it("starts the marks where the last line ends, right of the centre", () => {
    const placed = only(MARKED, 1);
    expect(placed.anchor).toBe("start");
    expect(placed.textX).toBeCloseTo(placed.left);
    expect(placed.marksLine).toBe(placed.lines.length - 1);
    const last = placed.lines.at(-1)!;
    expect(placed.marksX).toBeCloseTo(placed.textX + eight(last));
  });

  it("puts the marks before the last line, left of the centre", () => {
    const placed = only(MARKED, 3);
    expect(placed.anchor).toBe("end");
    expect(placed.textX).toBeCloseTo(placed.left + placed.width);
    const last = placed.lines.at(-1)!;
    expect(placed.marksX).toBeCloseTo(
      placed.textX - eight(last) - 2 * MARK_WIDTH,
    );
  });

  it("centres the last line and its marks together at the top", () => {
    const placed = only(label("AROUND KAFKA", { marks: 2 }), 0);
    expect(placed.anchor).toBe("middle");
    expect(placed.textX).toBeCloseTo(-MARK_WIDTH);
    expect(placed.marksX).toBeCloseTo(-MARK_WIDTH + (12 * 8) / 2);
    expect(placed.left).toBeCloseTo(-(12 * 8 + 2 * MARK_WIDTH) / 2);
    expect(placed.width).toBeCloseTo(12 * 8 + 2 * MARK_WIDTH);
  });
});

const ALICE = [
  label("AROUND POSTGRES", { marks: 2, reached: true }),
  label("AROUND REACT"),
  label("AROUND OBSERVABILITY", { marks: 1 }),
  label("Development", { kind: "named", marks: 1, reached: true }),
  label("Infrastructure", { kind: "named", marks: 1 }),
  label("Styling", { kind: "named" }),
];

/** Chromium's widths for alice's words in the phone's compact faces, measured in the running app. */
const CHROMIUM_AT_PHONE: Record<string, number> = {
  AROUND: 43,
  POSTGRES: 53.5,
  REACT: 33.3,
  OBSERVABILITY: 80.1,
  Development: 69.8,
  Infrastructure: 75,
  Styling: 38,
};

const chromiumAtPhone: MeasureText = (text, style) =>
  text
    .split(" ")
    .map((word) => CHROMIUM_AT_PHONE[word] ?? measuredLike(word, style))
    .reduce((sum, width) => sum + width + 3, -3);

function isCut(l: PlacedLabel): boolean {
  return l.lines.some((line) => line.endsWith("…"));
}

describe("the radius before a cut", () => {
  it.each([293, 311])(
    "keeps every one of a %ipx phone drawing's names whole, the drawing above its floor",
    (width) => {
      const layout = layoutShape(ALICE, width, chromiumAtPhone);
      expect(layout.labels.every((l) => l.visible && !isCut(l))).toBe(true);
      expect(layout.radius).toBeGreaterThan(0.22 * width);
      expectInside(layout);
    },
  );

  it("gives a top name the whole width, so its marks never cut it", () => {
    const top = layoutShape(ALICE, 311, measuredLike).labels[0]!;
    expect(top.anchor).toBe("middle");
    expect(top.lines.join(" ")).toBe("AROUND POSTGRES");
  });

  it("puts the marks on a line of their own rather than cutting a word for them", () => {
    const measure = (text: string) => text.length * 6;
    const layout = layoutShape(
      [
        label("AROUND KAFKA", { reached: true }),
        label("OBSERVABILITY", { marks: 2 }),
        label("AROUND GO"),
        label("AROUND JAVA", { reached: true }),
        label("AROUND RUST"),
        label("AROUND GO"),
      ],
      311,
      measure,
    );
    const side = layout.labels[1]!;
    expect(side.lines).toEqual(["OBSERVABILITY"]);
    expect(side.marksLine).toBe(1);
    expect(side.height).toBe(2 * LINE_HEIGHT);
    expect(side.marksX).toBeCloseTo(side.textX);
  });

  it("takes the largest radius at which the names stay whole, and the ceiling when every name fits there", () => {
    expect(layoutShape(ALICE, 704).radius).toBe(150);
    const phone = layoutShape(ALICE, 311, measuredLike);
    const wider = layoutShape(ALICE, 343, measuredLike);
    expect(wider.radius).toBeGreaterThan(phone.radius);
  });

  it("cuts a side name at the floor rather than growing the drawing out past it", () => {
    const layout = layoutShape(
      [
        label("AROUND REACT", { reached: true }),
        label("Infrastructure", { kind: "named", reached: true }),
        label("AROUND POSTGRES", { reached: true }),
        label("Infrastructure", { kind: "named", reached: true }),
      ],
      293,
    );
    expectInside(layout);
    expect(layout.radius).toBeLessThan(293 / 2);
    expect(layout.labels.map(isCut)).toEqual([false, true, false, true]);
    expect(layout.labels[1]!.lines[0]!.length).toBeGreaterThan(4);
  });

  it("keeps marks that need a line of their own inside the width", () => {
    const layout = layoutShape(
      [
        label("AROUND OBSERVABILITY", { marks: 1, reached: true }),
        label("Data", { kind: "named", marks: 2, reached: true }),
        label("AROUND JAVA", { marks: 2, reached: true }),
        label("Data", { kind: "named", marks: 2, reached: true }),
      ],
      293,
    );
    expectInside(layout);
    expect(layout.labels[1]!.lines).toEqual(["Data"]);
  });

  it("shrinks only as far as keeps a word no line can hold as long as at the floor, and cuts only that word", () => {
    const giant = "Supercalifragilisticexpialidocious";
    const alone = layoutShape(ALICE, 311, measuredLike);
    const layout = layoutShape(
      [...ALICE.slice(0, 5), label(giant, { kind: "named", reached: true })],
      311,
      measuredLike,
    );
    expect(layout.radius).toBeGreaterThan(0.22 * 311);
    expect(layout.radius).toBeLessThan(alone.radius);
    expectInside(layout);
    expect(isCut(layout.labels[5]!)).toBe(true);
    expect(layout.labels.slice(0, 5).some(isCut)).toBe(false);
  });
});
