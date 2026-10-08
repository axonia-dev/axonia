export function slugify(text: string): string {
  // Non-alnum runs already collapse to a single "-" below, so at most one leading/
  // trailing dash can remain - trimmed with plain string ops (a `^-+|-+$` regex
  // trips eslint's sonarjs/slow-regex check even though it can't backtrack here).
  let slug = text
    .trim()
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-");
  if (slug.startsWith("-")) slug = slug.slice(1);
  if (slug.endsWith("-")) slug = slug.slice(0, -1);
  return slug;
}
