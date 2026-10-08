import type {
  MapContribution,
  MapPerson,
} from "@/features/expertise/domain/expertise";
import { DELETED_MEMBER } from "@/features/identity/domain/user";

const KIND_WORDS: Record<MapContribution["evidence"]["sourceType"], string> = {
  SLOT: "a talk",
  ARTIFACT: "an artifact",
  TOPIC: "a topic",
  ACTION_ITEM: "an action item",
};

type People = ReadonlyMap<string, MapPerson>;

/** The only place a face's person is named for assistive technology: nothing is painted into the canvas as text. */
export function labelsFor(
  items: MapContribution[],
  people: People,
): Map<number, string> {
  return new Map(
    items.map((c) => [
      c.signalId,
      `${c.evidence.label ?? c.canonicalTag}, ${KIND_WORDS[c.evidence.sourceType]} by ${
        people.get(c.userId)?.displayName ?? DELETED_MEMBER
      }`,
    ]),
  );
}

export function authorOf(
  contribution: MapContribution | null,
  people: People,
): MapPerson | null {
  return contribution === null
    ? null
    : (people.get(contribution.userId) ?? null);
}
