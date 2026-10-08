const AFTER_SEPARATOR = /(?<=[/.:_?&=#@-])(?=[^/.:_?&=#@-])/;

/**
 * An identifier (a URL, a key, a DNS record) cut after each run of separators, so a line can end
 * where a person would split it rather than in the middle of a word. A run stays whole, so
 * `http://` never ends a line between its two slashes.
 */
export function identifierSegments(value: string): string[] {
  return value.split(AFTER_SEPARATOR);
}
