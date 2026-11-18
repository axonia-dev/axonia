import { describe, expect, it } from "vitest";

import type {
  SelfArea,
  SelfDeclaration,
} from "@/features/expertise/domain/expertise";
import {
  practitionerShape,
  type Shape,
  type ShapeGroup,
  type ShapeInput,
} from "@/features/expertise/domain/practitioner-shape";

const PLATFORM = 1;

function group(label: string, tags: string[], slot: number): ShapeGroup {
  return {
    label,
    spoken: label.toLowerCase(),
    tags,
    slot,
    kind: "named",
    anchor: tags[0]!,
  };
}

function named(count: number): ShapeGroup[] {
  return Array.from({ length: count }, (_, i) =>
    group(`Area ${i}`, [`a${i}`], i),
  );
}

function area(canonicalTag: string, tier: SelfArea["tier"]): SelfArea {
  return {
    canonicalTag,
    tier,
    hidden: false,
    communities: [],
    communityTiers: [{ communityId: PLATFORM, tier }],
    evidence: {
      presentedCount: 0,
      contributedCount: 0,
      offeredCount: 0,
      recent: [],
    },
  };
}

function declaration(tag: string): SelfDeclaration {
  return {
    tag,
    communityId: PLATFORM,
    communityName: "Platform Guild",
    communitySlug: "platform",
    hidden: false,
    declaredAt: "2026-09-01T00:00:00Z",
  };
}

function shape(
  groups: ShapeGroup[],
  loose: string[],
  over: Partial<ShapeInput>,
): Shape {
  return practitionerShape({
    groups,
    drawnTags: [...groups.flatMap((g) => g.tags), ...loose],
    optedOut: false,
    communityId: PLATFORM,
    areas: [],
    declarations: [],
    wantToLearn: [],
    ...over,
  });
}

function drawn(result: Shape) {
  if (result.kind !== "drawn")
    throw new Error(`expected drawn, got ${result.kind}`);
  return result;
}

const MANY = Array.from({ length: 40 }, (_, i) => `tag-${i}`);

describe("practitionerShape over areas in no group", () => {
  it("makes each loose area an axis after the groups, by name, while every axis fits", () => {
    const result = drawn(
      shape([group("Security", ["authn"], 0)], ["rust", "go", "css"], {
        areas: [area("go", "CONTRIBUTOR")],
      }),
    );
    expect(result.axes.map((a) => [a.label, a.kind, a.reach])).toEqual([
      ["Security", "named", null],
      ["css", "area", null],
      ["go", "area", "CONTRIBUTOR"],
      ["rust", "area", null],
    ]);
    expect(result.offAxis).toEqual([]);
  });

  it("still makes a loose area an axis at exactly twelve axes", () => {
    const result = drawn(
      shape(named(11), ["on-call"], {
        areas: [area("on-call", "CONTRIBUTOR")],
      }),
    );
    expect(result.axes).toHaveLength(12);
    expect(result.offAxis).toEqual([]);
  });

  it("draws only the groups past twelve axes, naming by name the loose areas the member holds", () => {
    const result = drawn(
      shape(named(14), ["on-call", "mailgun", "dora"], {
        areas: [area("a0", "PRACTITIONER")],
        declarations: [declaration("on-call")],
        wantToLearn: ["dora"],
      }),
    );
    expect(result.axes.map((a) => a.label)).toEqual(
      named(14).map((g) => g.label),
    );
    expect(result.offAxis).toEqual(["dora", "on-call"]);
    expect(result.unplacedWantToLearn).toEqual([]);
  });

  it("names a loose area at thirteen axes, and draws when it is the only thing held", () => {
    const result = drawn(
      shape(named(12), ["on-call"], {
        areas: [area("on-call", "CONTRIBUTOR")],
      }),
    );
    expect(result.axes).toHaveLength(12);
    expect(result.axes.every((a) => a.reach === null)).toBe(true);
    expect(result.offAxis).toEqual(["on-call"]);
  });

  it("says the map does not group the areas when fewer than three groups exist and the rest would not fit", () => {
    expect(
      shape(named(2), MANY, { areas: [area("tag-3", "PRACTITIONER")] }).kind,
    ).toBe("ungrouped");
  });

  it("is absent, not ungrouped, when the member holds nothing", () => {
    expect(shape([], MANY, {}).kind).toBe("absent");
  });

  it("is absent, not ungrouped, for an opted-out member", () => {
    expect(
      shape([], MANY, {
        optedOut: true,
        areas: [area("tag-3", "PRACTITIONER")],
      }).kind,
    ).toBe("absent");
  });

  it("draws the named areas when they hold every drawn area", () => {
    const result = drawn(
      shape(
        [
          group("A", ["a1", "a2"], 0),
          group("B", ["b1"], 1),
          group("C", ["c1"], 2),
        ],
        [],
        { areas: [area("b1", "CONTRIBUTOR")] },
      ),
    );
    expect(result.axes.map((a) => a.label)).toEqual(["A", "B", "C"]);
  });
});
