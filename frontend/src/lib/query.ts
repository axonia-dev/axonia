/**
 * Serialise a flat params object into a URL query string (leading "?" included),
 * skipping `undefined` and empty-string values. Shared by the HTTP gateway
 * adapters so each slice doesn't reimplement the same encoding.
 */
export function buildQuery(
  params: Record<string, string | number | undefined>,
): string {
  const search = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== "") {
      search.set(key, String(value));
    }
  }
  const serialized = search.toString();
  return serialized ? `?${serialized}` : "";
}
