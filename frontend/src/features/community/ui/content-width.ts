/**
 * Content max-width for the community shell's <main>, by route. Two pages draw
 * rather than read and take the wide width: the schedule needs room for a
 * calendar and, beside it, the panel a chip opens, so that the two stand side
 * by side rather than one over the other; the expertise map needs room for a
 * territory of a few thousand marks, and at the reading width its drawing
 * column came out under 400px on every screen size. Every other page keeps the
 * narrower reading width, the member's own expertise page included, and the
 * expertise page narrows itself back for its own search view. Pure so it is
 * unit-testable without rendering the shell.
 */
const WIDE_PAGES = new Set(["schedule", "expertise"]);

function pageOf(pathname: string): string {
  const [, , ...page] = pathname.split("/").filter(Boolean);
  return page.join("/");
}

export function contentMaxWidthClass(pathname: string): string {
  return WIDE_PAGES.has(pageOf(pathname)) ? "max-w-[2048px]" : "max-w-3xl";
}
