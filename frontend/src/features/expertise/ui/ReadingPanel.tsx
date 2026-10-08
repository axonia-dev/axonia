import { ArrowLeft } from "lucide-react";

import { Select } from "@/components/ui/Select";
import type {
  TopicLink,
  TopicNode,
} from "@/features/expertise/domain/expertise";
import { compareTags } from "@/features/expertise/ui/compare-tags";
import {
  type Reading,
  READING_OPTIONS,
  type ReadingId,
} from "@/features/expertise/ui/expertise-readings";
import type { DrawnGroup } from "@/features/expertise/ui/named-area-grouping";
import { NeighbourhoodSummary } from "@/features/expertise/ui/NeighbourhoodSummary";
import { plural, pluralWord } from "@/lib/plural";

/** "neighbourhoods" is the base rendering, not a reading; the select holds both so there is one control, not two. */
export type PanelSelection = ReadingId | "neighbourhoods";

/** Once a leader has named one, what is listed is no longer all neighbourhoods, so the base rendering goes by "groups". */
function groupsNoun(groups: DrawnGroup[]): string {
  return groups.some((g) => g.kind === "named") ? "groups" : "neighbourhoods";
}

function optionsFor(noun: string) {
  const label = noun.charAt(0).toUpperCase() + noun.slice(1);
  return [{ id: "neighbourhoods" as const, label }, ...READING_OPTIONS];
}

interface Props {
  topics: TopicNode[];
  links: TopicLink[];
  groups: DrawnGroup[];
  reading: PanelSelection;
  /** The reading already built by the view, which draws the map from the very same answer. Null under
   *  "neighbourhoods", which is the base rendering rather than a reading. */
  built: Reading | null;
  onReadingChange: (next: PanelSelection) => void;
  selectedTag: string | null;
  /** Null asks for the neighbourhood summary back, which is the one way out of an area's own page. */
  onSelectTag: (tag: string | null) => void;
  onSeeWhoKnows: (tag: string) => void;
}

export function ReadingPanel({
  topics,
  links,
  groups,
  reading,
  built,
  onReadingChange,
  selectedTag,
  onSelectTag,
  onSeeWhoKnows,
}: Readonly<Props>) {
  return (
    <aside
      aria-label="Topic readings"
      className="border-border bg-surface flex flex-col gap-4 p-4 md:border-l"
    >
      <div className="flex flex-col gap-1.5">
        <Select
          label="Reading"
          value={reading}
          options={optionsFor(groupsNoun(groups))}
          onChange={onReadingChange}
        />
        {/* Unsaid, a ringed area's absence from every reading reads as missing data rather than as the rule, and a
            ring with nothing inside it reads as a drawing fault. */}
        <p className="text-muted-foreground text-xs">
          Readings count activity. A dashed ring marks people open to questions.
          A ring with nothing inside it is an area that was offered and never
          practised. No reading counts either.
        </p>
      </div>
      {built === null ? (
        <NeighbourhoodPane
          selectedTag={selectedTag}
          groups={groups}
          topics={topics}
          links={links}
          onSelectTag={onSelectTag}
          onSeeWhoKnows={onSeeWhoKnows}
        />
      ) : (
        <ReadingRows built={built} />
      )}
    </aside>
  );
}

function NeighbourhoodPane({
  selectedTag,
  groups,
  topics,
  links,
  onSelectTag,
  onSeeWhoKnows,
}: Readonly<{
  selectedTag: string | null;
  groups: DrawnGroup[];
  topics: TopicNode[];
  links: TopicLink[];
  onSelectTag: (tag: string | null) => void;
  onSeeWhoKnows: (tag: string) => void;
}>) {
  if (selectedTag === null) {
    return (
      <NeighbourhoodSummary
        groups={groups}
        topics={topics}
        links={links}
        onSelectTag={onSelectTag}
      />
    );
  }
  return (
    <TopicDetail
      tag={selectedTag}
      topics={topics}
      links={links}
      groups={groups}
      onSelectTag={onSelectTag}
      onSeeWhoKnows={onSeeWhoKnows}
    />
  );
}

function ReadingRows({ built }: Readonly<{ built: Reading }>) {
  return (
    <div>
      <p className="bg-surface-sunken text-foreground rounded-xl px-3 py-2.5 text-sm leading-relaxed">
        {built.statement}
      </p>
      <ul>
        {built.rows.map((row, index) => (
          <li
            key={row.tag}
            className="border-border flex items-center gap-2.5 border-t py-1.5 text-sm"
          >
            {built.kind === "rank" && (
              <span className="bg-surface-sunken text-muted-foreground font-display grid size-6 shrink-0 place-items-center rounded-lg text-xs">
                {index + 1}
              </span>
            )}
            <span className="min-w-0 flex-1 font-medium break-words">
              {row.tag}
            </span>
            <span className="text-muted-foreground text-right">
              {row.value}
            </span>
          </li>
        ))}
      </ul>
      {built.kind === "rank" && (
        <p className="border-border text-muted-foreground mt-2 border-t pt-2 text-xs leading-relaxed">
          Showing {built.rows.length} of {built.total}. Order is the reading,
          not a verdict.
        </p>
      )}
    </div>
  );
}

/** Practitioners, then who is open to questions when anyone is. */
function practitionerLine(topic: TopicNode): string {
  const open = `${plural(topic.openToQuestionsCount, "person")} open to questions`;
  if (topic.practitionerCount === 0) return open;
  const practitioners = plural(topic.practitionerCount, "practitioner");
  if (topic.openToQuestionsCount === 0) return practitioners;
  return `${practitioners} · ${open}`;
}

function membershipOf(group: DrawnGroup): string {
  return group.kind === "named" ? `in ${group.name}` : group.name;
}

function TopicDetail({
  tag,
  topics,
  links,
  groups,
  onSelectTag,
  onSeeWhoKnows,
}: Readonly<{
  tag: string;
  topics: TopicNode[];
  links: TopicLink[];
  groups: DrawnGroup[];
  onSelectTag: (next: string | null) => void;
  onSeeWhoKnows: (next: string) => void;
}>) {
  const topic = topics.find((t) => t.canonicalTag === tag);
  const group = groups.find((g) => g.tags.includes(tag));
  const adjacent = links
    .filter((l) => l.a === tag || l.b === tag)
    .map((l) => ({ tag: l.a === tag ? l.b : l.a, weight: l.weight }))
    .sort((x, y) => y.weight - x.weight || compareTags(x.tag, y.tag));

  if (topic === undefined) return null;

  return (
    <div className="flex flex-1 flex-col gap-4">
      <div>
        <button
          type="button"
          onClick={() => onSelectTag(null)}
          className="text-muted-foreground hover:text-foreground focus-visible:ring-focus-ring mb-2 flex cursor-pointer items-center gap-1.5 rounded-lg text-sm outline-none focus-visible:ring-2"
        >
          <ArrowLeft size={16} aria-hidden />
          All {groupsNoun(groups)}
        </button>
        <h2 className="font-display text-lg font-semibold break-words">
          {topic.canonicalTag}
        </h2>
        <p className="text-muted-foreground text-sm">
          {practitionerLine(topic)}
          {group !== undefined && ` · ${membershipOf(group)}`}
        </p>
      </div>
      <div>
        <p className="text-muted-foreground mb-1.5 text-xs font-semibold tracking-widest uppercase">
          Alongside
        </p>
        <ul className="flex flex-wrap gap-1.5">
          {adjacent.map((entry) => (
            <li key={entry.tag}>
              <button
                type="button"
                onClick={() => onSelectTag(entry.tag)}
                aria-label={`${entry.tag}, ${entry.weight} shared ${pluralWord(entry.weight, "person")}`}
                className="border-border-strong text-muted-foreground hover:border-primary hover:text-primary-strong cursor-pointer rounded-full border px-2.5 py-1 text-xs"
              >
                <span className="text-foreground font-medium">{entry.tag}</span>{" "}
                {entry.weight}
              </button>
            </li>
          ))}
        </ul>
      </div>
      <button
        type="button"
        onClick={() => onSeeWhoKnows(tag)}
        className="bg-primary text-primary-foreground mt-auto cursor-pointer rounded-xl px-3.5 py-2.5 text-sm font-semibold"
      >
        See who knows {tag}
      </button>
    </div>
  );
}
