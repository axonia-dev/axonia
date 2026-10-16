import type { Page } from "@playwright/test";
import { expect, test } from "@playwright/test";

import {
  community,
  member,
  paged,
  shellOfCommunity,
  SLUG,
  stubReads,
} from "./community-home";

const SESSION_ID = 42;
const TITLE = "Virtual threads deep dive";
const PIXEL_PNG = Buffer.from(
  "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==",
  "base64",
);

const pictured = {
  ...community,
  heroUrl: "/morph-hero.png",
  badgeUrl: "/morph-badge.png",
};

const group = (part: string, id: string) =>
  `::view-transition-group(morph-${part}-${id})`;
const communityGroups = ["banner", "badge", "name"].map((part) =>
  group(part, `community-${SLUG}`),
);
const sessionGroups = ["surface", "title"].map((part) =>
  group(part, `session-${SESSION_ID}`),
);

const inTwoDays = new Date(Date.now() + 2 * 86_400_000).toISOString();

const occurrence = {
  seriesId: null,
  title: TITLE,
  status: "SCHEDULED",
  occurrenceDate: null,
  scheduledAt: inTwoDays,
  hasTime: true,
  durationMinutes: 60,
  seriesTimeZone: null,
  place: null,
  sections: [],
};

const session = {
  ...occurrence,
  id: SESSION_ID,
  communityId: community.id,
  facilitatorUserId: null,
  attendeeCount: 0,
  notes: null,
  recordingUrl: null,
  placeOverride: null,
  seriesPlace: null,
  createdAt: "2026-05-31T10:00:00Z",
  updatedAt: "2026-05-31T10:00:00Z",
};

const scheduleEntry = { ...occurrence, sessionId: SESSION_ID, virtual: false };

const savedSession = {
  kind: "session",
  session: { ...session, slotCount: 0 },
  community: { ...community, accentColor: null },
  savedAt: "2026-06-02T00:00:00Z",
};

/** Every morph group any frame has painted since the document loaded or since `forgetMorphs`. */
async function recordMorphs(page: Page): Promise<void> {
  await page.addInitScript(() => {
    const record = globalThis as { morphsSeen?: Set<string> };
    record.morphsSeen = new Set();
    const sample = () => {
      for (const animation of document.getAnimations()) {
        const pseudo =
          (animation.effect as KeyframeEffect | null)?.pseudoElement ?? "";
        if (pseudo.startsWith("::view-transition-group(morph-")) {
          record.morphsSeen?.add(pseudo);
        }
      }
      requestAnimationFrame(sample);
    };
    requestAnimationFrame(sample);
  });
}

function morphsSeen(page: Page): Promise<string[]> {
  return page.evaluate(() => [
    ...((globalThis as { morphsSeen?: Set<string> }).morphsSeen ?? []),
  ]);
}

async function forgetMorphs(page: Page): Promise<void> {
  await page.evaluate(() => {
    (globalThis as { morphsSeen?: Set<string> }).morphsSeen?.clear();
  });
}

async function groupsDuring(
  page: Page,
  act: () => Promise<unknown>,
  expected: string[],
): Promise<void> {
  await forgetMorphs(page);
  await act();
  await expect
    .poll(() => morphsSeen(page), { timeout: 2000 })
    .toEqual(expect.arrayContaining(expected));
}

/** Settles a few frames past the destination, so a morph that started is sure to be recorded. */
async function noGroupsDuring(
  page: Page,
  act: () => Promise<unknown>,
  arrived: () => Promise<void>,
): Promise<void> {
  await forgetMorphs(page);
  await act();
  await arrived();
  await page.evaluate(
    () =>
      new Promise((done) => {
        setTimeout(done, 600);
      }),
  );
  expect(await morphsSeen(page)).toEqual([]);
}

/** The lobby lists its two sections from one path, told apart by the membership parameter. */
async function stubLobby(
  page: Page,
  sections: { joined: unknown[]; explore: unknown[] },
): Promise<void> {
  await page.route(
    (url) => url.pathname === "/axonia/communities",
    (route) => {
      const { searchParams } = new URL(route.request().url());
      const joined = searchParams.get("membership") === "JOINED";
      const content = joined ? sections.joined : sections.explore;
      return route.fulfill({ json: paged(content) });
    },
  );
}

async function stubImages(page: Page): Promise<void> {
  await page.route(
    (url) => url.pathname.startsWith("/morph-"),
    (route) => route.fulfill({ contentType: "image/png", body: PIXEL_PNG }),
  );
}

async function visitLobby(page: Page): Promise<void> {
  await recordMorphs(page);
  await stubReads(page, {
    ...shellOfCommunity,
    [`/axonia/communities/${SLUG}`]: pictured,
  });
  await stubLobby(page, {
    joined: [{ ...pictured, role: "ORGANIZER" }],
    explore: [],
  });
  await stubImages(page);
  await page.goto("/communities");
}

const tileOf = (page: Page) =>
  page.getByRole("link", { name: community.name }).first();
const heroHeading = (page: Page) =>
  page.getByRole("heading", { name: community.name });

async function visitSession(page: Page, from: string): Promise<void> {
  await recordMorphs(page);
  await stubReads(page, {
    ...shellOfCommunity,
    [`/axonia/communities/${SLUG}/schedule`]: [scheduleEntry],
    [`/axonia/sessions/${SESSION_ID}`]: session,
    "/axonia/me/saved": [savedSession],
  });
  await page.goto(from);
}

test("a lobby tile morphs into its hero, and back replays the morph", async ({
  page,
}) => {
  // given
  await visitLobby(page);
  const tile = tileOf(page);

  // when / then
  await groupsDuring(page, () => tile.click(), communityGroups);
  await expect(page).toHaveURL(`/communities/${SLUG}`);
  await expect(heroHeading(page)).toBeVisible();

  // when / then
  await groupsDuring(page, () => page.goBack(), communityGroups);
  await expect(page).toHaveURL("/communities");
  await expect(tile).toBeInViewport();
});

test("a schedule card morphs into its session", async ({ page }) => {
  // given
  await visitSession(page, `/communities/${SLUG}/schedule?view=timeline`);

  // when / then
  await groupsDuring(
    page,
    () => page.getByRole("link", { name: TITLE }).click(),
    sessionGroups,
  );
  await expect(page).toHaveURL(`/communities/${SLUG}/sessions/${SESSION_ID}`);
});

test("the breadcrumb is a plain link and never morphs", async ({ page }) => {
  // given
  await visitSession(page, `/communities/${SLUG}/sessions/${SESSION_ID}`);
  const breadcrumb = page.getByRole("navigation", { name: "Breadcrumb" });
  await expect(breadcrumb).toContainText(TITLE);

  // when / then
  await noGroupsDuring(
    page,
    () => breadcrumb.getByRole("link", { name: community.name }).click(),
    () => expect(heroHeading(page)).toBeVisible(),
  );
});

test("a saved session morphs into its session page", async ({ page }) => {
  // given
  await visitSession(page, "/me/saved");

  // when / then
  await groupsDuring(
    page,
    () => page.getByRole("link", { name: TITLE }).click(),
    sessionGroups,
  );
  await expect(page).toHaveURL(`/communities/${SLUG}/sessions/${SESSION_ID}`);
});

test("joining from Explore plays the welcome and never morphs", async ({
  page,
}) => {
  // given
  await recordMorphs(page);
  await stubReads(page, {
    ...shellOfCommunity,
    [`/axonia/communities/${SLUG}`]: pictured,
    [`/axonia/communities/${SLUG}/members`]: { ...member, role: "MEMBER" },
  });
  await stubLobby(page, { joined: [], explore: [{ ...pictured, role: null }] });
  await stubImages(page);
  await page.goto("/communities");
  const stage = page.locator("[data-ceremony-stage]");

  // when / then
  await noGroupsDuring(
    page,
    () => page.getByRole("button", { name: "Join" }).click(),
    () => expect(stage).toBeVisible(),
  );
  await expect(page).toHaveURL(`/communities/${SLUG}`);
});

test("reduced motion opens the hero without a morph", async ({ page }) => {
  // given
  await page.emulateMedia({ reducedMotion: "reduce" });
  await visitLobby(page);

  // when / then
  await noGroupsDuring(
    page,
    () => tileOf(page).click(),
    () => expect(heroHeading(page)).toBeVisible(),
  );
});
