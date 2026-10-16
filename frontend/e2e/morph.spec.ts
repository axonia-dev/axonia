import type { Page } from "@playwright/test";
import { expect, test } from "@playwright/test";

import {
  community,
  member,
  shellOfCommunity,
  SLUG,
  stubReads,
} from "./community-home";
import {
  group,
  groupsDuring,
  morphsSeen,
  noGroupsDuring,
  recordMorphs,
  stubImages,
  stubLobby,
} from "./morph-support";

const SESSION_ID = 42;
const TITLE = "Virtual threads deep dive";

const pictured = {
  ...community,
  heroUrl: "/morph-hero.png",
  badgeUrl: "/morph-badge.png",
};

const communityGroups = ["banner", "badge", "name"].map((part) =>
  group(part, `community-${SLUG}`),
);
const sessionGroups = ["surface", "title"].map((part) =>
  group(part, `session-${SESSION_ID}`),
);

const inTwoDays = new Date(Date.now() + 2 * 86_400_000).toISOString();

function agenda(talk: string) {
  const slot = {
    id: 1,
    sectionId: 1,
    position: 0,
    presenterUserIds: [],
    topicId: null,
    title: talk,
    notes: null,
    durationMinutes: 30,
    tags: [],
  };
  return [
    {
      id: 1,
      sessionId: SESSION_ID,
      position: 0,
      sectionTypeId: null,
      sectionType: null,
      sectionTypeIcon: null,
      sectionTypeAccentColor: null,
      title: null,
      durationMinutes: 30,
      slots: [slot],
    },
  ];
}

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
  sections: agenda(TITLE),
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
  session: { ...session, slotCount: 1 },
  community: { ...community, accentColor: null },
  savedAt: "2026-06-02T00:00:00Z",
};

async function visitLobby(page: Page): Promise<void> {
  await recordMorphs(page);
  await stubReads(page, {
    ...shellOfCommunity,
    [`/axonia/communities/${SLUG}`]: pictured,
  });
  const fillers = Array.from({ length: 14 }, (_, index) => ({
    ...community,
    id: 100 + index,
    slug: `filler-${index}`,
    name: `Filler ${index}`,
    role: "MEMBER",
  }));
  await stubLobby(page, {
    joined: [...fillers, { ...pictured, role: "ORGANIZER" }],
    explore: [],
  });
  await stubImages(page);
  await page.goto("/communities");
}

const tileOf = (page: Page) =>
  page.getByRole("link", { name: community.name }).first();
const heroHeading = (page: Page) =>
  page.getByRole("heading", { name: community.name });

const mainScrollTop = (page: Page) =>
  page.locator("main").evaluate((main) => main.scrollTop);

/**
 * Points at the session link and clicks once the reads it waits for have answered. A morph only
 * plays when its destination is ready within the 300 ms budget (use-morph-navigate.ts); a
 * route-fulfilled read under a loaded parallel run can take longer, and the app then rightly
 * navigates without morphing. Pointing first is what a reader does, and it starts the same reads.
 */
async function openSession(page: Page, reads: string[]): Promise<void> {
  const link = page.getByRole("link", { name: TITLE });
  const answered = reads.map((path) =>
    page.waitForResponse(
      (response) => new URL(response.url()).pathname === path,
    ),
  );
  await link.hover();
  await Promise.all(answered);
  await link.click();
}

const SESSION_READ = `/axonia/sessions/${SESSION_ID}`;
const COMMUNITY_READ = `/axonia/communities/${SLUG}`;

async function visitSession(
  page: Page,
  from: string,
  talk = TITLE,
): Promise<void> {
  await recordMorphs(page);
  await stubReads(page, {
    ...shellOfCommunity,
    [`/axonia/communities/${SLUG}/schedule`]: [
      { ...scheduleEntry, sections: agenda(talk) },
    ],
    [`/axonia/sessions/${SESSION_ID}`]: { ...session, sections: agenda(talk) },
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
  await tile.scrollIntoViewIfNeeded();
  expect(await mainScrollTop(page)).toBeGreaterThan(0);

  // when / then
  await groupsDuring(page, () => tile.click(), communityGroups);
  await expect(page).toHaveURL(`/communities/${SLUG}`);
  await expect(heroHeading(page)).toBeVisible();

  // when / then
  await groupsDuring(page, () => page.goBack(), communityGroups);
  await expect(page).toHaveURL("/communities");
  await expect(tile).toBeInViewport();
  expect(await mainScrollTop(page)).toBeGreaterThan(0);
});

test("a schedule card morphs into its session", async ({ page }) => {
  // given
  await visitSession(page, `/communities/${SLUG}/schedule?view=timeline`);

  // when / then
  await groupsDuring(
    page,
    () => openSession(page, [SESSION_READ]),
    sessionGroups,
  );
  await expect(page).toHaveURL(`/communities/${SLUG}/sessions/${SESSION_ID}`);
});

test("a schedule card the session page heads otherwise morphs without its title", async ({
  page,
}) => {
  // given
  await visitSession(
    page,
    `/communities/${SLUG}/schedule?view=timeline`,
    "Signals in React 19",
  );

  // when / then
  await groupsDuring(page, () => openSession(page, [SESSION_READ]), [
    group("surface", `session-${SESSION_ID}`),
  ]);
  await expect(page).toHaveURL(`/communities/${SLUG}/sessions/${SESSION_ID}`);
  expect(await morphsSeen(page)).not.toContain(
    group("title", `session-${SESSION_ID}`),
  );
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
    () => openSession(page, [SESSION_READ, COMMUNITY_READ]),
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

  // then the hero image survives the ceremony's end instead of being decoded again
  const banners = page.locator("img[data-focal-image]");
  await banners.evaluateAll((imgs) => {
    for (const img of imgs) img.setAttribute("data-kept", "");
  });
  await expect(stage).toBeHidden({ timeout: 15_000 });
  await expect(banners.first()).toBeVisible();
  await expect(
    page.locator("img[data-focal-image]:not([data-kept])"),
  ).toHaveCount(0);
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
