import type { Page } from "@playwright/test";

import { E2E_USER_ID, stubShell } from "./api";
import { signedIn } from "./session";

const apiGlob = "**/axonia/**";
export const SLUG = "backend-guild";

export function paged<T>(content: T[]) {
  return {
    content,
    page: {
      totalElements: content.length,
      totalPages: 1,
      number: 0,
      size: 50,
    },
  };
}

export const community = {
  id: 1,
  slug: SLUG,
  name: "La communauté de l'anneau",
  kindId: 1,
  kind: "Guild",
  description: "We backend",
  status: "ACTIVE",
  memberCount: 3,
  createdAt: "2026-05-31T10:00:00Z",
  updatedAt: "2026-05-31T10:00:00Z",
};

export const member = {
  id: 9,
  communityId: 1,
  userId: E2E_USER_ID,
  role: "ORGANIZER",
  joinedAt: "2026-05-31T10:00:00Z",
  updatedAt: "2026-05-31T10:00:00Z",
};

/** The community shell's own reads, shared by both pages under test. */
export const shellOfCommunity: Record<string, unknown> = {
  [`/axonia/communities/${SLUG}`]: community,
  [`/axonia/communities/${SLUG}/members/me`]: member,
  [`/axonia/communities/${SLUG}/management-policy`]: {},
  "/axonia/communities": paged([{ ...community, role: "ORGANIZER" }]),
  "/axonia/me/onboarding": { state: "COMPLETED" },
};

/** Answers keyed by exact pathname, so `/artifacts` and `/artifacts/stats` cannot shadow each
 *  other the way a prefix table lets them. Anything unlisted is a 404, which every read here
 *  degrades to an empty surface rather than a broken one. */
export async function stubReads(
  page: Page,
  answers: Record<string, unknown>,
): Promise<void> {
  const byPath = new Map(Object.entries(answers));
  await page.route(apiGlob, (route) => {
    const { pathname } = new URL(route.request().url());
    const body = byPath.get(pathname);
    return route.fulfill({
      status: body === undefined ? 404 : 200,
      contentType: "application/json",
      body: JSON.stringify(body ?? {}),
    });
  });
  await signedIn(page);
  await stubShell(page);
}

/** A 90-day activity trace, oldest first, with the given acts on the days given as days ago. */
export function activityTrace(
  acts: Partial<
    Record<"sessionsHeld" | "libraryAdditions" | "joins", number[]>
  >,
) {
  const series = (daysAgo: number[] = []) =>
    Array.from(
      { length: 90 },
      (_, day) => daysAgo.filter((ago) => ago === 89 - day).length,
    );
  return {
    days: 90,
    sessionsHeld: series(acts.sessionsHeld),
    libraryAdditions: series(acts.libraryAdditions),
    joins: series(acts.joins),
  };
}
