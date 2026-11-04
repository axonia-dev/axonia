/** Extracts a plain-array query's data, defaulting to empty. */
export function queryItems<T>(query: { data?: T[] | undefined }): T[] {
  return query.data ?? [];
}
