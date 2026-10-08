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
const DATA = 2;

function group(
  label: string,
  tags: string[],
  slot: number,
  kind: ShapeGroup["kind"] = "neighbourhood",
): ShapeGroup {
  return {
    label,
    spoken: label.toLowerCase(),
    tags,
    slot,
    kind,
    anchor: tags[0]!,
  };
}

const GROUPS = [
  group("AROUND KAFKA", ["kafka", "flink"], 0),
  group("Observability", ["otel", "grafana"], 1, "named"),
  group("AROUND KUBERNETES", ["kubernetes", "helm"], 2),
  group("AROUND REACT", ["react"], 3),
];
const DRAWN = GROUPS.flatMap((g) => g.tags);

function area(
  canonicalTag: string,
  tiers: [number, SelfArea["tier"]][],
  over: Partial<SelfArea> = {},
): SelfArea {
  return {
    canonicalTag,
    tier: "PRACTITIONER",
    hidden: false,
    communities: [],
    communityTiers: tiers.map(([communityId, tier]) => ({ communityId, tier })),
    evidence: {
      presentedCount: 0,
      contributedCount: 0,
      offeredCount: 0,
      recent: [],
    },
    ...over,
  };
}

function declaration(
  tag: string,
  communityId = PLATFORM,
  hidden = false,
): SelfDeclaration {
  return {
    tag,
    communityId,
    communityName: "Platform Guild",
    communitySlug: "platform",
    hidden,
    declaredAt: "2026-09-01T00:00:00Z",
  };
}

function shape(over: Partial<ShapeInput>): Shape {
  return practitionerShape({
    groups: GROUPS,
    drawnTags: DRAWN,
    belowVocabularyFloor: false,
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

describe("practitionerShape", () => {
  it("draws every group in slot order, reaching the strongest tier held in this community", () => {
    const result = drawn(
      shape({
        areas: [
          area("flink", [[PLATFORM, "INTERESTED"]]),
          area("kafka", [[PLATFORM, "PRACTITIONER"]]),
          area("otel", [[PLATFORM, "CONTRIBUTOR"]]),
        ],
        groups: [...GROUPS].reverse(),
      }),
    );
    expect(result.axes.map((a) => [a.label, a.reach])).toEqual([
      ["AROUND KAFKA", "PRACTITIONER"],
      ["Observability", "CONTRIBUTOR"],
      ["AROUND KUBERNETES", null],
      ["AROUND REACT", null],
    ]);
  });

  it("ignores the tier another community reads", () => {
    const result = drawn(
      shape({
        areas: [
          area("kafka", [[DATA, "PRACTITIONER"]]),
          area("otel", [[PLATFORM, "INTERESTED"]]),
        ],
      }),
    );
    expect(result.axes[0]!.reach).toBeNull();
  });

  it("is absent when the member holds no tier the community reads", () => {
    expect(shape({ areas: [area("kafka", [], { hidden: true })] }).kind).toBe(
      "absent",
    );
  });

  it("is absent while the self view comes from a server too old to say each community's tier, rather than draw no reach", () => {
    const older = area("kafka", []);
    delete older.communityTiers;
    expect(
      shape({
        areas: [older, area("otel", [[PLATFORM, "CONTRIBUTOR"]])],
        declarations: [declaration("react")],
        wantToLearn: ["helm"],
      }).kind,
    ).toBe("absent");
  });

  it("is absent for an opted-out member, whatever they hold", () => {
    expect(
      shape({
        optedOut: true,
        areas: [area("kafka", [[PLATFORM, "PRACTITIONER"]])],
        declarations: [declaration("react")],
      }).kind,
    ).toBe("absent");
  });

  it("is absent when the member holds nothing in this community", () => {
    expect(
      shape({
        areas: [area("kafka", [[DATA, "PRACTITIONER"]])],
        declarations: [declaration("react", DATA)],
      }).kind,
    ).toBe("absent");
  });

  it("marks a declared axis with and without a reach, and ignores a hidden or foreign declaration", () => {
    const result = drawn(
      shape({
        areas: [area("kafka", [[PLATFORM, "PRACTITIONER"]])],
        declarations: [
          declaration("flink"),
          declaration("react"),
          declaration("helm", PLATFORM, true),
          declaration("otel", DATA),
        ],
      }),
    );
    expect(result.axes.map((a) => a.declared)).toEqual([
      true,
      false,
      false,
      true,
    ]);
    expect(result.axes[0]!.reach).toBe("PRACTITIONER");
    expect(result.axes[3]!.reach).toBeNull();
  });

  it("marks an axis holding an area to learn, and names the ones the map does not draw", () => {
    const result = drawn(
      shape({
        areas: [area("kafka", [[PLATFORM, "INTERESTED"]])],
        wantToLearn: ["helm", "threat-modeling", "rust"],
      }),
    );
    expect(result.axes.map((a) => a.wantsToLearn)).toEqual([
      false,
      false,
      true,
      false,
    ]);
    expect(result.unplacedWantToLearn).toEqual(["rust", "threat-modeling"]);
  });

  it("draws when the only thing held is an area to learn off the map", () => {
    const result = drawn(shape({ wantToLearn: ["rust"] }));
    expect(result.axes.every((a) => a.reach === null)).toBe(true);
    expect(result.unplacedWantToLearn).toEqual(["rust"]);
  });

  it("below the vocabulary floor, makes each ungrouped area an axis after the groups, by name", () => {
    const result = drawn(
      shape({
        groups: [group("Security", ["authn"], 0, "named")],
        drawnTags: ["authn", "rust", "go", "css"],
        belowVocabularyFloor: true,
        areas: [area("go", [[PLATFORM, "CONTRIBUTOR"]])],
      }),
    );
    expect(result.axes.map((a) => [a.label, a.kind, a.reach])).toEqual([
      ["Security", "named", null],
      ["css", "area", null],
      ["go", "area", "CONTRIBUTOR"],
      ["rust", "area", null],
    ]);
  });

  it("at or above the floor, says the map does not group the areas when detection left some ungrouped", () => {
    expect(
      shape({
        groups: [],
        drawnTags: Array.from({ length: 40 }, (_, i) => `tag-${i}`),
        areas: [area("tag-3", [[PLATFORM, "PRACTITIONER"]])],
      }).kind,
    ).toBe("ungrouped");
  });

  it("is absent, not ungrouped, when a degenerate detection leaves areas ungrouped but the member holds nothing", () => {
    expect(
      shape({
        groups: [],
        drawnTags: Array.from({ length: 40 }, (_, i) => `tag-${i}`),
      }).kind,
    ).toBe("absent");
  });

  it("is absent, not ungrouped, for an opted-out member under a degenerate detection", () => {
    expect(
      shape({
        optedOut: true,
        groups: [],
        drawnTags: Array.from({ length: 40 }, (_, i) => `tag-${i}`),
        areas: [area("tag-3", [[PLATFORM, "PRACTITIONER"]])],
      }).kind,
    ).toBe("absent");
  });

  it("draws the named areas when they hold every drawn area under a degenerate detection", () => {
    const named = [
      group("A", ["a1", "a2"], 0, "named"),
      group("B", ["b1"], 1, "named"),
      group("C", ["c1"], 2, "named"),
    ];
    const result = drawn(
      shape({
        groups: named,
        drawnTags: ["a1", "a2", "b1", "c1"],
        areas: [area("b1", [[PLATFORM, "CONTRIBUTOR"]])],
      }),
    );
    expect(result.axes.map((a) => a.label)).toEqual(["A", "B", "C"]);
  });

  it("is absent with fewer than three axes", () => {
    expect(
      shape({
        groups: GROUPS.slice(0, 2),
        drawnTags: GROUPS.slice(0, 2).flatMap((g) => g.tags),
        areas: [area("kafka", [[PLATFORM, "PRACTITIONER"]])],
      }).kind,
    ).toBe("absent");
  });

  it("never treats open to questions as a reach", () => {
    const result = drawn(
      shape({
        areas: [
          area("kafka", [[PLATFORM, "OPEN_TO_QUESTIONS"]]),
          area("otel", [[PLATFORM, "INTERESTED"]]),
        ],
      }),
    );
    expect(result.axes[0]!.reach).toBeNull();
  });
});
