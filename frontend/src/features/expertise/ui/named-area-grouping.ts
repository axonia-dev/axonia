import type {
  TopicLink,
  TopicNode,
} from "@/features/expertise/domain/expertise";
import type { NamedArea } from "@/features/expertise/domain/named-area";
import { withSlots } from "@/features/expertise/ui/group-slots";
import {
  anchorOf,
  detectNeighbourhoods,
  MIN_VOCABULARY,
  type Neighbourhood,
  neighbourhoodNames,
} from "@/features/expertise/ui/topic-neighbourhoods";
import { plural } from "@/lib/plural";

/**
 * A group the map draws: one a leader named, or one detection found in what the leader left. Its slot is its colour
 * and its place on the map, and is not its position in any list.
 */
export type DrawnGroup = Neighbourhood & {
  name: string;
  kind: "named" | "neighbourhood";
  slot: number;
};

type Unslotted = Omit<DrawnGroup, "slot">;

function drawnNamedAreas(
  drawn: ReadonlySet<string>,
  links: TopicLink[],
  namedAreas: NamedArea[],
): Unslotted[] {
  return namedAreas.flatMap((namedArea) => {
    const tags = namedArea.tags.filter((tag) => drawn.has(tag));
    if (tags.length === 0) return [];
    const anchor = anchorOf(tags, links);
    return [{ tags, anchor, name: namedArea.name, kind: "named" as const }];
  });
}

function asDetected(neighbourhood: Neighbourhood): Unslotted {
  return {
    ...neighbourhood,
    name: neighbourhoodNames(neighbourhood).list,
    kind: "neighbourhood",
  };
}

/**
 * The vocabulary floor is the community's, not the remainder's: a community big enough to be grouped still has what
 * a leader left grouped, however little that is, or naming its largest neighbourhood would dissolve every other one.
 */
function detectedIn(
  topics: TopicNode[],
  links: TopicLink[],
  taken: ReadonlySet<string>,
): Unslotted[] {
  if (topics.length < MIN_VOCABULARY) return [];
  const rest = topics.filter((t) => !taken.has(t.canonicalTag));
  const restLinks = links.filter((l) => !taken.has(l.a) && !taken.has(l.b));
  return detectNeighbourhoods(rest, restLinks, 0).map(asDetected);
}

/**
 * Named areas as the leader said them, even at one tag. Detection groups only the remainder, over the links inside it:
 * a link into a named area would otherwise pull the remainder under a label no group of it carries.
 *
 * Creating one named area must not repaint or move the rest of a map the reader has already learnt, so each group's
 * slot is matched against what detection finds over every topic, as if nothing were named. The neighbourhoods are
 * listed first; a list that shows named areas first orders them itself.
 */
export function groupsFor(
  topics: TopicNode[],
  links: TopicLink[],
  namedAreas: NamedArea[],
): DrawnGroup[] {
  const named = drawnNamedAreas(
    new Set(topics.map((t) => t.canonicalTag)),
    links,
    namedAreas,
  );
  const taken = new Set(named.flatMap((group) => group.tags));
  const detected = detectedIn(topics, links, taken);
  const baseline =
    taken.size === 0 ? detected : detectNeighbourhoods(topics, links);
  const slotted = withSlots(baseline, detected, named);
  return [...slotted.detected, ...slotted.named];
}

/** A named area is called what the leader typed, as typed: an uppercased name is no longer the one they chose. */
export function groupNames(group: Omit<DrawnGroup, "slot">): {
  short: string;
  list: string;
  accessible: string;
} {
  if (group.kind === "neighbourhood") return neighbourhoodNames(group);
  return {
    short: group.name,
    list: group.name,
    accessible: `${group.name}, named area with ${plural(group.tags.length, "area")}`,
  };
}
