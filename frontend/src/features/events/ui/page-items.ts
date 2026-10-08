/** Extracts the content array from a paged-list query, defaulting to empty. */
export function pageItems<T>(query: {
  data?: { content: T[] } | undefined;
}): T[] {
  return query.data?.content ?? [];
}

/** Extracts a plain-array query's data, defaulting to empty. */
export function queryItems<T>(query: { data?: T[] | undefined }): T[] {
  return query.data ?? [];
}
