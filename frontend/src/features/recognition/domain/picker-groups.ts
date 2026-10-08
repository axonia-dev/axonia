import { COMMON_REACTIONS } from "@/features/recognition/domain/common-reactions";
import type { EmojiGroup } from "@/features/recognition/domain/emoji-search";
import { searchEmoji } from "@/features/recognition/domain/emoji-search";
import type {
  ReactionDefinition,
  ReactionRef,
} from "@/features/recognition/domain/reaction";

export const COMMON_HEADING = "Common";
export const FREQUENT_HEADING = "Frequently used";

/** What the picker draws: one pickable thing, already carrying the ref a pick hands back and
 *  the word a reader hears. Shaped like `ReactionTally` on purpose - one nullable arm per kind -
 *  so the same glyph box draws a picker entry and a placed chip without a second shape between
 *  them. */
export interface PickerEntry {
  /** Unique inside its own group; the same emoji may legitimately appear in two groups. */
  key: string;
  name: string;
  ref: ReactionRef;
  emoji: string | null;
  imageUrl: string | null;
}

export interface PickerGroup {
  heading: string;
  entries: PickerEntry[];
}

export interface PickerGroupsInput {
  definitions: ReactionDefinition[];
  catalog: EmojiGroup[];
  /** This member's own last picks, newest first. Characters only: every approved custom
   *  reaction already stands in the organization's own group above, so a custom recent could
   *  only ever repeat one two rows further down. */
  recents: readonly string[];
  term: string;
  organizationName: string | undefined;
}

/** Named after the organization once its name is known; until then a word that fits an
 *  enterprise organization as well as a hub, since both may hold reactions of their own. */
function organizationHeading(name: string | undefined): string {
  return name ?? "Organization";
}

function emojiEntry(emoji: string, name: string): PickerEntry {
  return {
    key: emoji,
    name,
    ref: { kind: "EMOJI", emoji },
    emoji,
    imageUrl: null,
  };
}

function customEntry(definition: ReactionDefinition): PickerEntry {
  return {
    key: String(definition.id),
    name: definition.name,
    ref: { kind: "CUSTOM", definitionId: definition.id },
    emoji: null,
    imageUrl: definition.imageUrl,
  };
}

/** Only an approved definition is pickable; one still awaiting approval isn't a real
 *  reaction yet. */
function ownEntries(
  definitions: ReactionDefinition[],
  term: string,
): PickerEntry[] {
  const needle = term.toLowerCase();
  return definitions
    .filter((d) => !d.pending && d.name.toLowerCase().includes(needle))
    .map(customEntry);
}

function namesByCharacter(catalog: EmojiGroup[]): Map<string, string> {
  return new Map(
    catalog.flatMap((group) => group.emoji.map((e) => [e.c, e.n] as const)),
  );
}

/** A character the catalog no longer carries is dropped rather than drawn nameless: the
 *  catalog is the one source of the word, and an unnamed button is one nothing can read out. */
function namedEntries(
  characters: readonly string[],
  names: Map<string, string>,
): PickerEntry[] {
  return characters.flatMap((c) => {
    const name = names.get(c);
    return name === undefined ? [] : [emojiEntry(c, name)];
  });
}

function group(heading: string, entries: PickerEntry[]): PickerGroup[] {
  return entries.length === 0 ? [] : [{ heading, entries }];
}

/** The common set and this member's recents, which exist to shorten a browse and would only
 *  fragment a search. Absent while a term is typed for exactly that reason. */
function curatedGroups(
  catalog: EmojiGroup[],
  recents: readonly string[],
): PickerGroup[] {
  const names = namesByCharacter(catalog);
  const fresh = recents.filter((c) => !COMMON_REACTIONS.includes(c));
  return [
    ...group(COMMON_HEADING, namedEntries(COMMON_REACTIONS, names)),
    ...group(FREQUENT_HEADING, namedEntries(fresh, names)),
  ];
}

/**
 * Every group the picker draws, in the order it draws them: the organization's own reactions,
 * the common set, this member's recents, then the standard catalog. Pure, so the whole of the
 * picker's ordering, filtering and naming is testable without a DOM or a fetch.
 */
export function pickerGroups({
  definitions,
  catalog,
  recents,
  term,
  organizationName,
}: Readonly<PickerGroupsInput>): PickerGroup[] {
  const needle = term.trim();
  return [
    ...group(
      organizationHeading(organizationName),
      ownEntries(definitions, needle),
    ),
    ...(needle === "" ? curatedGroups(catalog, recents) : []),
    ...searchEmoji(catalog, needle).map((standard) => ({
      heading: standard.group,
      entries: standard.emoji.map((e) => emojiEntry(e.c, e.n)),
    })),
  ];
}
