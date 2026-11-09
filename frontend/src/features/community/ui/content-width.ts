/**
 * Content max-width for the community shell's <main>, by route. Two pages draw
 * rather than read and take the wide width: the schedule needs room for a
 * calendar and, beside it, the panel a chip opens, so that the two stand side
 * by side rather than one over the other; the expertise map needs room for a
 * territory of a few thousand marks, and at the reading width its drawing
 * column came out under 400px on every screen size. Every other page keeps the
 * narrower reading width, the member's own expertise page included, and the
 * expertise page narrows itself back for its own search view. A session page
 * sits between the two: its run sheet shares the row with a 13rem rail, which
 * at the reading width left its slot cards around 500px wide, yet a slot card
 * is text and stops reading well long before the drawing width. The health
 * page is a dashboard: a glance row of four tiles and two panels side by side
 * do not fit the reading width, yet its panels are read, not drawn. Pure so it
 * is unit-testable without rendering the shell.
 */
const WIDE_PAGES = new Set(["schedule", "expertise"]);

function pageOf(pathname: string): string {
  const [, , ...page] = pathname.split("/").filter(Boolean);
  return page.join("/");
}

function isSessionPage(page: string): boolean {
  return /^sessions\/[^/]+$/.test(page);
}

export function contentMaxWidthClass(pathname: string): string {
  const page = pageOf(pathname);
  if (WIDE_PAGES.has(page)) return "max-w-[2048px]";
  if (isSessionPage(page)) return "max-w-6xl";
  if (page === "health") return "max-w-5xl";
  return "max-w-3xl";
}
