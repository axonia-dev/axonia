import { z } from "zod";

const pageMetaSchema = z.object({
  size: z.number(),
  number: z.number(),
  totalElements: z.number(),
  totalPages: z.number(),
});

/**
 * Parses Spring Data's stable {@code PagedModel} envelope ({@code { content, page: {...} }}) and
 * flattens it into our {@link Page} domain shape, keeping the wire format from leaking past the
 * adapter boundary.
 */
export function pageSchema<T extends z.ZodType>(item: T) {
  return z
    .object({
      content: z.array(item),
      page: pageMetaSchema,
    })
    .transform(({ content, page }) => ({
      content,
      totalElements: page.totalElements,
      totalPages: page.totalPages,
      number: page.number,
      size: page.size,
    }));
}

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

/**
 * Where removing a row leaves the reader: back one page when it took the last row off this one.
 *
 * Left where they were, they would be past the end of a now-shorter list, on a page that answers
 * empty beside a heading counting everything.
 */
export function pageAfterRemoval(page: number, rowsLeft: number): number {
  return rowsLeft === 0 && page > 0 ? page - 1 : page;
}
