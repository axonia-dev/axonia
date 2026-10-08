import type { TopicNode } from "@/features/expertise/domain/expertise";
import { plural } from "@/lib/plural";

export type ReadingId = "concentrated" | "unpresented" | "recent";

export const READING_OPTIONS = [
  { id: "concentrated", label: "Concentrated in one person" },
  { id: "unpresented", label: "Not yet presented" },
  { id: "recent", label: "Recently active" },
] as const satisfies ReadonlyArray<{ id: ReadingId; label: string }>;

const SHOWN_RANK = 5;

interface ReadingRow {
  tag: string;
  value: string;
}

export interface Reading {
  id: ReadingId;
  /** A fact lists its whole set; an ordering shows the leading few. */
  kind: "fact" | "rank";
  statement: string;
  rows: ReadingRow[];
  total: number;
  badgeOf: (tag: string) => string | null;
}

const MONTH_MS = 1000 * 60 * 60 * 24 * 30.44;

function firstName(full: string): string {
  return full.split(" ")[0] ?? full;
}

function pct(share: number): number {
  return Math.round(share * 100);
}

function monthsSince(iso: string, now: Date): number {
  return Math.max(0, Math.round((now.getTime() - Date.parse(iso)) / MONTH_MS));
}

/** A recency reading's top row is usually the current month, so "0 months ago" is its common case, not its edge. */
function monthsAgo(months: number): string {
  return months === 0 ? "this month" : `${plural(months, "month")} ago`;
}

/** The areas with recorded activity, which `recent` orders by. Each reading picks by what it measures, so
 *  this is not the shared rule that keeps a declaration out of all three, only the one recency uses. */
function withActivity(topics: TopicNode[]): TopicNode[] {
  return topics.filter((topic) => topic.lastActivityAt !== null);
}

interface Shape {
  kind: "fact" | "rank";
  pick: (topics: TopicNode[]) => TopicNode[];
  value: (t: TopicNode, now: Date) => string;
  statement: (picked: TopicNode[], all: TopicNode[], now: Date) => string;
  badge: (t: TopicNode, now: Date) => string | null;
}

const SHAPES: Record<ReadingId, Shape> = {
  concentrated: {
    kind: "rank",
    pick: (topics) =>
      topics
        .filter((t) => t.signalCount > 0)
        .sort(
          (a, b) =>
            b.topHolderShare - a.topHolderShare ||
            b.signalCount - a.signalCount,
        ),
    value: (t) => {
      const share = `${pct(t.topHolderShare)}% of ${t.signalCount}`;
      return t.topHolderName === null
        ? share
        : `${share} · ${firstName(t.topHolderName)}`;
    },
    // The denominator is what this reading can measure, not every area: an area nobody has practised
    // cannot rest on one person, and counting it would understate the share.
    statement: (picked) => {
      const alone = picked.filter((t) => t.practitionerCount === 1).length;
      if (alone === 0) {
        return `The most concentrated area is ${pct(picked[0]!.topHolderShare)}% one person's work.`;
      }
      const verb = alone === 1 ? "rests" : "rest";
      return `${alone} of ${plural(picked.length, "area")} ${verb} on a single person.`;
    },
    badge: (t) => `${pct(t.topHolderShare)}%`,
  },
  unpresented: {
    kind: "fact",
    pick: (topics) =>
      topics
        .filter(
          (t) =>
            t.presentedCount === 0 &&
            (t.contributedCount > 0 || t.offeredCount > 0),
        )
        .sort(
          (a, b) =>
            b.contributedCount +
            b.offeredCount -
            (a.contributedCount + a.offeredCount),
        ),
    // "proposal", not "offer": an OFFERED signal is somebody putting a topic forward to present,
    // which is neither the dashed ring's willingness to be asked nor a word this page ever uses.
    value: (t) => {
      const artifacts =
        t.contributedCount > 0 ? plural(t.contributedCount, "artifact") : null;
      const proposals =
        t.offeredCount > 0 ? plural(t.offeredCount, "proposal") : null;
      return `${[artifacts, proposals].filter((part) => part !== null).join(", ")}, no talk`;
    },
    statement: (picked, all) => {
      if (withActivity(all).length === 0) return "Nothing to read here yet.";
      if (picked.length === 0)
        return "Every area with activity has been presented.";
      const verb = picked.length === 1 ? "has" : "have";
      return `${plural(picked.length, "area")} ${verb} not been presented yet.`;
    },
    badge: (t) => String(t.contributedCount + t.offeredCount),
  },
  recent: {
    kind: "rank",
    pick: (topics) =>
      withActivity(topics).sort(
        (a, b) => Date.parse(b.lastActivityAt!) - Date.parse(a.lastActivityAt!),
      ),
    value: (t, now) => monthsAgo(monthsSince(t.lastActivityAt!, now)),
    statement: (picked, _all, now) =>
      `The most recent activity was ${monthsAgo(monthsSince(picked[0]!.lastActivityAt!, now))}.`,
    badge: (t, now) => `${monthsSince(t.lastActivityAt!, now)}m`,
  },
};

/**
 * Builds one reading. Nothing here has a threshold: one reading is a fact, two are orderings, and every row shows
 * the value it was ordered by so the reader judges rather than the product.
 */
export function buildReading(
  id: ReadingId,
  topics: TopicNode[],
  now: Date,
): Reading {
  const shape = SHAPES[id];
  const picked = shape.pick(topics);
  const rows = (
    shape.kind === "rank" ? picked.slice(0, SHOWN_RANK) : picked
  ).map((t) => ({ tag: t.canonicalTag, value: shape.value(t, now) }));
  const measured = new Map(picked.map((t) => [t.canonicalTag, t] as const));

  return {
    id,
    kind: shape.kind,
    statement:
      picked.length === 0 && shape.kind === "rank"
        ? "Nothing to read here yet."
        : shape.statement(picked, topics, now),
    rows,
    total: picked.length,
    badgeOf: (tag) => {
      const t = measured.get(tag);
      return t === undefined ? null : shape.badge(t, now);
    },
  };
}
