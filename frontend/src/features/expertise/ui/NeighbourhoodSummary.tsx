import type {
  TopicLink,
  TopicNode,
} from "@/features/expertise/domain/expertise";
import {
  type DrawnGroup,
  groupNames,
} from "@/features/expertise/ui/named-area-grouping";
import { cn } from "@/lib/cn";
import { plural } from "@/lib/plural";

/** Named areas lead, since the list does: "4 named areas, 3 detected". */
function countStatement(groups: DrawnGroup[]): string {
  const named = groups.filter((g) => g.kind === "named").length;
  const detected = groups.length - named;
  if (named === 0) return `${plural(detected, "neighbourhood")}.`;
  if (detected === 0) return `${plural(named, "named area")}.`;
  return `${plural(named, "named area")}, ${detected} detected.`;
}

/** The leader's own groups lead the list, ahead of what detection found. */
function listed(groups: DrawnGroup[]): DrawnGroup[] {
  return [
    ...groups.filter((g) => g.kind === "named"),
    ...groups.filter((g) => g.kind === "neighbourhood"),
  ];
}

/** The largest is looked for rather than read off the front; on a tie the group listed first wins. */
function largestOf(groups: DrawnGroup[]): DrawnGroup {
  return groups.reduce(
    (largest, group) =>
      group.tags.length > largest.tags.length ? group : largest,
    groups[0]!,
  );
}

/**
 * The closest honest analogue to seeing the map: what a glance would have carried, said in a sentence. An analogue,
 * not an equivalent, and it must not be described as one.
 */
function shapeStatement(
  groups: DrawnGroup[],
  topics: TopicNode[],
  links: TopicLink[],
): string {
  const linked = new Set(links.flatMap((l) => [l.a, l.b]));
  const apart = topics.filter((t) => !linked.has(t.canonicalTag)).length;
  const parts: string[] = [];

  if (groups.length === 0) {
    parts.push(`${plural(topics.length, "area")}, not yet grouped.`);
  } else {
    const largest = largestOf(listed(groups));
    parts.push(
      countStatement(groups),
      `The largest is ${groupNames(largest).list}, with ${plural(largest.tags.length, "area")}.`,
    );
  }
  if (apart > 0) {
    parts.push(
      `${plural(apart, "area")} ${apart === 1 ? "shares" : "share"} no link with any other.`,
    );
  }
  return parts.join(" ");
}

export function NeighbourhoodSummary({
  groups,
  topics,
  links,
  onSelectTag,
}: Readonly<{
  groups: DrawnGroup[];
  topics: TopicNode[];
  links: TopicLink[];
  onSelectTag: (tag: string) => void;
}>) {
  return (
    <div className="flex flex-col gap-3">
      <p className="text-muted-foreground text-sm leading-relaxed">
        {shapeStatement(groups, topics, links)}
      </p>
      <ul className="flex flex-wrap gap-1.5">
        {listed(groups).map((group) => (
          <li key={group.anchor}>
            <button
              type="button"
              onClick={() => onSelectTag(group.anchor)}
              aria-label={groupNames(group).accessible}
              data-kind={group.kind}
              className={cn(
                "border-border-strong text-muted-foreground hover:border-primary hover:text-primary-strong cursor-pointer rounded-full border px-2.5 py-1 text-xs",
                group.kind === "named" && "text-foreground font-medium",
              )}
            >
              {groupNames(group).list}
              <span className="ml-1.5 opacity-70">{group.tags.length}</span>
            </button>
          </li>
        ))}
      </ul>
    </div>
  );
}
