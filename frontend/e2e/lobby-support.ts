import type { Page } from "@playwright/test";

import { community, paged } from "./community-home";

type Shelf = { key: string; items: unknown[]; hasMore?: boolean };

/** A lobby card as the lobby read sends it: a community listing and its showcase signals. */
export function lobbyItem(overrides: Record<string, unknown> = {}) {
  return {
    ...community,
    role: null,
    nextSession: null,
    nextSessionThisWeek: false,
    latestSession: null,
    joinedLast30Days: 0,
    faces: [],
    stats: { sessionsHeld: 0, artifactsShared: 0 },
    lately: { artifactTitle: null, topicTitle: null },
    ...overrides,
  };
}

/** `count` distinct cards, numbered from `from`, with whatever each of them shares. */
export function lobbyItems(
  from: number,
  count: number,
  shared: Record<string, unknown> = {},
) {
  return Array.from({ length: count }, (_, i) =>
    lobbyItem({
      id: from + i,
      slug: `community-${from + i}`,
      name: `Community ${from + i}`,
      ...shared,
    }),
  );
}

/** The lobby read, and the search beside it. Register after stubReads: the newest route wins. */
export async function stubLobby(
  page: Page,
  lobby: { billboard: unknown[]; shelves: Shelf[] },
  matches: unknown[] = [],
): Promise<void> {
  await page.route(
    (url) => url.pathname === "/axonia/lobby",
    (route) =>
      route.fulfill({
        json: {
          billboard: lobby.billboard,
          shelves: lobby.shelves.map((shelf) => ({ hasMore: false, ...shelf })),
        },
      }),
  );
  await page.route(
    (url) => url.pathname === "/axonia/lobby/search",
    (route) => route.fulfill({ json: paged(matches) }),
  );
}
