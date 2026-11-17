/**
 * Content max-width for the global shell's <main>, by route. Pages read at the reading width;
 * a mentoring journal opened from the global My mentoring takes the width it gets inside a
 * community (see `contentMaxWidthClass`), for its trail and its board of three columns.
 */
export function globalContentMaxWidthClass(pathname: string): string {
  return /^\/me\/mentoring\/[^/]+\/?$/.test(pathname)
    ? "max-w-5xl"
    : "max-w-3xl";
}
