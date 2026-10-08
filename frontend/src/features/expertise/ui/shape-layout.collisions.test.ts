import { describe, expect, it } from "vitest";

import {
  type LabelSpec,
  layoutShape,
  type PlacedLabel,
} from "@/features/expertise/ui/shape-layout";

const GUTTER = 4;

function label(text: string, over: Partial<LabelSpec> = {}): LabelSpec {
  return { text, kind: "neighbourhood", marks: 0, reached: false, ...over };
}

function overlap(a: PlacedLabel, b: PlacedLabel): boolean {
  const aBottom = a.top + a.height;
  const bBottom = b.top + b.height;
  return (
    a.left < b.left + b.width &&
    b.left < a.left + a.width &&
    a.top < bBottom &&
    b.top < aBottom
  );
}

function crowds(a: PlacedLabel, b: PlacedLabel): boolean {
  return overlap(
    { ...a, left: a.left - GUTTER, width: a.width + 2 * GUTTER },
    { ...b, top: b.top - GUTTER, height: b.height + 2 * GUTTER },
  );
}

function expectClear(layout: ReturnType<typeof layoutShape>) {
  const shown = layout.labels.filter((l) => l.visible);
  shown.forEach((a, i) => {
    for (const b of shown.slice(i + 1)) expect(overlap(a, b)).toBe(false);
  });
  for (const l of shown) {
    expect(l.left + layout.width / 2).toBeGreaterThanOrEqual(0);
    expect(l.left + l.width).toBeLessThanOrEqual(layout.width / 2);
    expect(l.top + l.height + layout.centreY).toBeLessThanOrEqual(
      layout.height,
    );
  }
}

function expectReachedShown(
  specs: readonly LabelSpec[],
  layout: ReturnType<typeof layoutShape>,
) {
  specs.forEach((spec, i) => {
    if (spec.reached) expect(layout.labels.at(i)!.visible).toBe(true);
  });
}

const SIXTEEN = [
  "KAFKA",
  "KUBERNETES",
  "POSTGRES",
  "REACT",
  "JAVA",
  "TERRAFORM",
  "GRAPHQL",
  "SPRING",
  "PYTHON",
  "RUST",
  "DOCKER",
  "REDIS",
  "ANGULAR",
  "GO",
  "SWIFT",
  "KOTLIN",
].map((name) => `AROUND ${name}`);

describe("layoutShape when names collide", () => {
  it("drops empty spokes' names until none overlap, and never a reached one", () => {
    const many = Array.from({ length: 24 }, (_, i) =>
      label(`AROUND AREA ${i}`, { reached: i % 3 === 0 }),
    );
    const layout = layoutShape(many, 704);
    expect(layout.labels.filter((l) => l.visible).length).toBeLessThan(
      many.length,
    );
    expectReachedShown(many, layout);
    expectClear(layout);
  });

  it.each([311, 343, 704])(
    "pushes reached neighbours apart at %ipx rather than letting them overlap",
    (width) => {
      const specs = SIXTEEN.map((text, i) =>
        label(text, { reached: i < 3 || i > 13 }),
      );
      const layout = layoutShape(specs, width);
      expectReachedShown(specs, layout);
      expectClear(layout);
    },
  );

  it.each([311, 343])(
    "keeps every other reached spoke apart at %ipx",
    (width) => {
      const specs = SIXTEEN.map((text, i) =>
        label(text, { reached: i % 2 === 0, marks: i % 4 === 0 ? 2 : 0 }),
      );
      const layout = layoutShape(specs, width);
      expectReachedShown(specs, layout);
      expectClear(layout);
    },
  );

  it("grows the drawing's height to push a reached name clear", () => {
    const quiet = SIXTEEN.map((text, i) => label(text, { reached: i === 0 }));
    const crowded = SIXTEEN.map((text, i) => label(text, { reached: i < 3 }));
    expect(layoutShape(crowded, 343).height).toBeGreaterThan(
      layoutShape(quiet, 343).height,
    );
  });

  it("drops an unmarked empty name before a marked one, whichever is wider", () => {
    const plain = SIXTEEN.map((text) => label(text));
    const widestFirst = layoutShape(plain, 343);
    expect(widestFirst.labels[1]!.visible).toBe(false);
    expect(widestFirst.labels[2]!.visible).toBe(true);

    const marked = plain.map((spec, i) =>
      i === 1 ? { ...spec, marks: 1 } : spec,
    );
    const markFirst = layoutShape(marked, 343);
    expect(markFirst.labels[1]!.visible).toBe(true);
    expect(markFirst.labels[2]!.visible).toBe(false);
    expectClear(markFirst);
  });

  it.each([311, 343, 704])(
    "hides at %ipx only an empty name that would crowd one shown",
    (width) => {
      for (const specs of [
        SIXTEEN.map((text, i) => label(text, { reached: i % 3 === 0 })),
        [
          label("AROUND KAFKA", { reached: true }),
          label("Observability", { kind: "named" }),
          label("Developer Experience and Platform Tooling Guild", {
            kind: "named",
            reached: true,
          }),
          label("AROUND POSTGRES", { marks: 1 }),
          label("AROUND REACT"),
          label("Security", { kind: "named", marks: 1 }),
          label("AROUND JAVA"),
        ],
      ]) {
        const layout = layoutShape(specs, width);
        const shown = layout.labels.filter((l) => l.visible);
        for (const hidden of layout.labels.filter((l) => !l.visible))
          expect(shown.some((other) => crowds(other, hidden))).toBe(true);
        expectClear(layout);
      }
    },
  );
});
