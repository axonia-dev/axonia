import type {
  SelfArea,
  SelfDeclaration,
  Tier,
} from "@/features/expertise/domain/expertise";

export type ShapeReach = Exclude<Tier, "OPEN_TO_QUESTIONS">;
export type AxisKind = "named" | "neighbourhood" | "area";

/** A group as the map draws it, reduced to what the shape reads. */
export interface ShapeGroup {
  tags: readonly string[];
  kind: "named" | "neighbourhood";
  slot: number;
  label: string;
  spoken: string;
  /** One of the group's own tags, unique to it: the axis key is built from this, never from the label. */
  anchor: string;
}

export interface ShapeAxis {
  key: string;
  kind: AxisKind;
  slot: number | null;
  label: string;
  spoken: string;
  reach: ShapeReach | null;
  declared: boolean;
  wantsToLearn: boolean;
}

export type Shape =
  | { kind: "absent" }
  | { kind: "ungrouped" }
  | { kind: "drawn"; axes: ShapeAxis[]; unplacedWantToLearn: string[] };

export interface ShapeInput {
  groups: readonly ShapeGroup[];
  drawnTags: readonly string[];
  belowVocabularyFloor: boolean;
  optedOut: boolean;
  communityId: number;
  areas: readonly SelfArea[];
  declarations: readonly SelfDeclaration[];
  wantToLearn: readonly string[];
}

const MIN_AXES = 3;
export const ABSENT: Shape = { kind: "absent" };

const REACH_ORDER: readonly ShapeReach[] = [
  "PRACTITIONER",
  "CONTRIBUTOR",
  "INTERESTED",
];

interface Holdings {
  reach: Map<string, ShapeReach>;
  declared: Set<string>;
  wanted: Set<string>;
}

type AxisBase = Pick<ShapeAxis, "key" | "kind" | "slot" | "label" | "spoken">;

const byName = (a: string, b: string) => a.localeCompare(b);

function isReach(tier: Tier | undefined): tier is ShapeReach {
  return tier !== undefined && tier !== "OPEN_TO_QUESTIONS";
}

function reachByTag(input: ShapeInput): Map<string, ShapeReach> {
  const reach = new Map<string, ShapeReach>();
  for (const area of input.areas) {
    const tier = area.communityTiers?.find(
      (t) => t.communityId === input.communityId,
    )?.tier;
    if (isReach(tier)) reach.set(area.canonicalTag, tier);
  }
  return reach;
}

function holdingsOf(input: ShapeInput): Holdings {
  const declared = input.declarations
    .filter((d) => d.communityId === input.communityId && !d.hidden)
    .map((d) => d.tag);
  return {
    reach: reachByTag(input),
    declared: new Set(declared),
    wanted: new Set(input.wantToLearn),
  };
}

/** A server older than the shape sends no community tiers; drawing then would show every axis at the centre. */
function readsEveryTier(input: ShapeInput): boolean {
  return input.areas.every((area) => area.communityTiers !== undefined);
}

function holdsAnything(h: Holdings): boolean {
  return h.reach.size + h.declared.size + h.wanted.size > 0;
}

function strongest(
  tags: readonly string[],
  reach: Map<string, ShapeReach>,
): ShapeReach | null {
  const held = new Set(tags.map((tag) => reach.get(tag)));
  return REACH_ORDER.find((r) => held.has(r)) ?? null;
}

function axisOf(
  base: AxisBase,
  tags: readonly string[],
  h: Holdings,
): ShapeAxis {
  return {
    ...base,
    reach: strongest(tags, h.reach),
    declared: tags.some((t) => h.declared.has(t)),
    wantsToLearn: tags.some((t) => h.wanted.has(t)),
  };
}

function groupAxes(groups: readonly ShapeGroup[], h: Holdings): ShapeAxis[] {
  const ordered = [...groups];
  ordered.sort((a, b) => a.slot - b.slot || byName(a.label, b.label));
  return ordered.map((g) =>
    axisOf(
      {
        key: g.anchor,
        kind: g.kind,
        slot: g.slot,
        label: g.label,
        spoken: g.spoken,
      },
      g.tags,
      h,
    ),
  );
}

function areaAxes(tags: readonly string[], h: Holdings): ShapeAxis[] {
  return tags.map((tag) =>
    axisOf(
      { key: `area:${tag}`, kind: "area", slot: null, label: tag, spoken: tag },
      [tag],
      h,
    ),
  );
}

function marksSomething(axis: ShapeAxis): boolean {
  return axis.reach !== null || axis.declared || axis.wantsToLearn;
}

function ungroupedTags(input: ShapeInput): string[] {
  const grouped = new Set(input.groups.flatMap((g) => g.tags));
  const ungrouped = input.drawnTags.filter((t) => !grouped.has(t));
  ungrouped.sort(byName);
  return ungrouped;
}

function unplaced(input: ShapeInput, wanted: Set<string>): string[] {
  const drawn = new Set(input.drawnTags);
  const off = [...wanted].filter((t) => !drawn.has(t));
  off.sort(byName);
  return off;
}

/** One member's practice in one community, over the groups the map draws; evidence alone reaches, marks never do. */
export function practitionerShape(input: ShapeInput): Shape {
  if (input.optedOut || !readsEveryTier(input)) return ABSENT;
  const holdings = holdingsOf(input);
  if (!holdsAnything(holdings)) return ABSENT;
  const ungrouped = ungroupedTags(input);
  if (ungrouped.length > 0 && !input.belowVocabularyFloor) {
    return { kind: "ungrouped" };
  }
  const axes = [
    ...groupAxes(input.groups, holdings),
    ...areaAxes(ungrouped, holdings),
  ];
  const unplacedWantToLearn = unplaced(input, holdings.wanted);
  const nothingToShow =
    !axes.some(marksSomething) && unplacedWantToLearn.length === 0;
  if (axes.length < MIN_AXES || nothingToShow) return ABSENT;
  return { kind: "drawn", axes, unplacedWantToLearn };
}
