export interface TextSegment {
  text: string;
  matched: boolean;
}

/**
 * `text` split into the runs that match `needle` and the runs that do not, case
 * insensitively. A blank needle yields one unmatched segment, so the caller needs no special
 * case. No regex is built, so a query such as `(` or `*` is matched literally rather than
 * throwing.
 */
export function markSegments(text: string, needle: string): TextSegment[] {
  const target = needle.trim().toLowerCase();
  if (target === "") return [{ text, matched: false }];

  const haystack = text.toLowerCase();
  const segments: TextSegment[] = [];
  let cursor = 0;
  for (
    let hit = haystack.indexOf(target);
    hit !== -1;
    hit = haystack.indexOf(target, cursor)
  ) {
    if (hit > cursor) {
      segments.push({ text: text.slice(cursor, hit), matched: false });
    }
    segments.push({
      text: text.slice(hit, hit + target.length),
      matched: true,
    });
    cursor = hit + target.length;
  }
  if (cursor < text.length) {
    segments.push({ text: text.slice(cursor), matched: false });
  }
  return segments.length === 0 ? [{ text, matched: false }] : segments;
}
