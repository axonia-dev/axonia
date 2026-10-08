interface CatalogEmoji {
  /** The character. */
  c: string;
  /** Its Unicode name, which is also what the search matches and what the label reads. */
  n: string;
}

export interface EmojiGroup {
  group: string;
  emoji: CatalogEmoji[];
}

/** Pure, so the picker's filtering is testable without the catalog or the DOM. */
export function searchEmoji(
  catalog: readonly EmojiGroup[],
  term: string,
): EmojiGroup[] {
  const needle = term.trim().toLowerCase();
  if (needle === "") {
    return [...catalog];
  }
  return catalog
    .map((group) => ({
      group: group.group,
      emoji: group.emoji.filter(
        (e) => e.n.toLowerCase().includes(needle) || e.c === term.trim(),
      ),
    }))
    .filter((group) => group.emoji.length > 0);
}

/** Lazy so the catalog never reaches the main bundle; the picker awaits this on open. */
export async function loadEmojiCatalog(): Promise<EmojiGroup[]> {
  const module =
    await import("@/features/recognition/domain/emoji-catalog.json");
  return module.default;
}
