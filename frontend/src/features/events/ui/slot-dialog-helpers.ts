/**
 * Private helpers for SlotDialog: budget math, topic ranking, and mutation
 * utilities used by both AddSlotForm and EditSlotForm.
 *
 * Not exported from the feature barrel; internal to the ui layer.
 */
import type { Section } from "@/features/events/domain/section";
import { sectionUsedMinutes } from "@/features/events/domain/section";
import type { Topic } from "@/features/events/domain/topic";

export const PROPOSED_PARAMS = {
  page: 0,
  size: 50,
  status: "PROPOSED",
} as const;
export const DEFAULT_MINUTES = 15;
export const MIN_MINUTES = 5;
export const MINUTE_STEP = 5;
const FALLBACK_MAX_MINUTES = 60;

export type Source = "backlog" | "newTopic";
export type SlotMode = "present" | "open";

export const SOURCE_OPTIONS = [
  { id: "backlog", label: "From backlog" },
  { id: "newTopic", label: "New topic" },
] as const;

export const MODE_OPTIONS = [
  { id: "present", label: "I'll present" },
  { id: "open", label: "Leave open" },
] as const;

function rankByVotes(topics: readonly Topic[]): Topic[] {
  return [...topics].sort((a, b) => b.voteCount - a.voteCount);
}

export function pickSelected(
  ranked: Topic[],
  topicId: number | null,
): Topic | null {
  const id = topicId ?? ranked[0]?.id ?? null;
  return ranked.find((t) => t.id === id) ?? null;
}

export function resolveSource(
  choice: Source | null,
  topicCount: number,
): Source {
  return choice ?? (topicCount > 0 ? "backlog" : "newTopic");
}

/** The description of the ranked topic with `id`, or "" when none matches. */
export function topicDescription(ranked: Topic[], id: number | null): string {
  return ranked.find((t) => t.id === id)?.description ?? "";
}

interface SliderBounds {
  remaining: number | null;
  maxMinutes: number;
}

/** excludeMinutes lets Edit mode discount the slot's own current time. */
export function sliderBounds(
  section: Section,
  excludeMinutes = 0,
): SliderBounds {
  const budget = section.durationMinutes;
  if (budget === null) {
    return { remaining: null, maxMinutes: FALLBACK_MAX_MINUTES };
  }
  return {
    remaining: budget - (sectionUsedMinutes(section) - excludeMinutes),
    maxMinutes: Math.max(budget, FALLBACK_MAX_MINUTES),
  };
}

export function budgetHint(
  remaining: number | null,
  minutes: number,
): { description?: string; descriptionTone?: "muted" | "warning" } {
  if (remaining === null) {
    return {};
  }
  if (minutes > remaining) {
    return {
      description: `${minutes - remaining} min over the section budget`,
      descriptionTone: "warning",
    };
  }
  return { description: `${remaining} min left in this section` };
}

export function canAdd(
  source: Source,
  subject: string,
  selected: Topic | null,
  busy: boolean,
): boolean {
  if (busy) {
    return false;
  }
  return source === "newTopic" ? subject.trim() !== "" : selected !== null;
}

export function proposedTopics(
  page: { content: Topic[] } | undefined,
): Topic[] {
  return rankByVotes(page?.content ?? []);
}

export function anyPending(
  ...mutations: ReadonlyArray<{ isPending: boolean }>
): boolean {
  return mutations.some((m) => m.isPending);
}

export function firstError(
  ...mutations: ReadonlyArray<{ error: unknown }>
): unknown {
  return mutations.find((m) => m.error != null)?.error ?? null;
}
