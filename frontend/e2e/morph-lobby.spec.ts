import type { Page } from "@playwright/test";
import { expect, test } from "@playwright/test";

import {
  community,
  member,
  shellOfCommunity,
  SLUG,
  stubReads,
} from "./community-home";
import { lobbyItem, lobbyItems, stubLobby } from "./lobby-support";
import {
  group,
  groupsDuring,
  noGroupsDuring,
  recordMorphs,
  stubImages,
} from "./morph-support";

const pictured = {
  ...community,
  heroUrl: "/morph-hero.png",
  badgeUrl: "/morph-badge.png",
};

const communityGroups = ["banner", "badge", "name"].map((part) =>
  group(part, `community-${SLUG}`),
);
const cardGroups = ["banner", "name"].map((part) =>
  group(part, `community-${SLUG}`),
);
async function visitLobby(
  page: Page,
  role: string | null = "ORGANIZER",
): Promise<void> {
  await recordMorphs(page);
  await stubReads(page, {
    ...shellOfCommunity,
    [`/axonia/communities/${SLUG}`]: pictured,
  });
  await stubLobby(page, {
    billboard: [],
    shelves: [
      {
        key: "OTHERS",
        items: [
          ...lobbyItems(100, 14, { role: "MEMBER" }),
          lobbyItem({ ...pictured, role }),
        ],
      },
    ],
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
test("a joined lobby card opens into its hero in one click, and back replays the morph into the card", async ({
  page,
}) => {
  // given
  await visitLobby(page);
  const tile = tileOf(page);
  await tile.scrollIntoViewIfNeeded();
  expect(await mainScrollTop(page)).toBeGreaterThan(0);

  // when / then - a card wears no badge, so its banner and name are what morph, out and back
  await groupsDuring(page, () => tile.click(), cardGroups);
  await expect(page).toHaveURL(`/communities/${SLUG}`);
  await expect(heroHeading(page)).toBeVisible();

  // when / then
  await groupsDuring(page, () => page.goBack(), cardGroups);
  await expect(page).toHaveURL("/communities");
  await expect(tile).toBeInViewport();
  expect(await mainScrollTop(page)).toBeGreaterThan(0);
});

test("an unjoined card's threshold peeks inside through the morph", async ({
  page,
}) => {
  // given
  await visitLobby(page, null);
  await page.getByRole("button", { name: community.name }).first().click();
  const peek = page
    .getByRole("dialog", { name: community.name })
    .getByRole("link", { name: "Peek inside" });

  // when / then
  await groupsDuring(page, () => peek.click(), communityGroups);
  await expect(page).toHaveURL(`/communities/${SLUG}`);
});

test("Open on a joined billboard slide morphs out of the slide, and back into it", async ({
  page,
}) => {
  // given - the community is the billboard's second slide and on a shelf; the slide on show wears its names
  await page.setViewportSize({ width: 1440, height: 900 });
  await recordMorphs(page);
  await stubReads(page, {
    ...shellOfCommunity,
    [`/axonia/communities/${SLUG}`]: pictured,
  });
  const joined = lobbyItem({ ...pictured, role: "ORGANIZER" });
  const other = lobbyItem({
    id: 2,
    slug: "data-guild",
    name: "Data Guild",
    role: "MEMBER",
  });
  await stubLobby(page, {
    billboard: [other, joined],
    shelves: [{ key: "JOINED", items: [other, joined] }],
  });
  await stubImages(page);
  await page.goto("/communities");
  const billboard = page.getByRole("region", { name: "Featured communities" });
  const ours = billboard.getByRole("group", {
    name: `${community.name}, 2 of 2`,
  });
  await billboard
    .getByRole("button", { name: `Show ${community.name}` })
    .click();
  await expect(ours).not.toHaveAttribute("inert");
  // Clicked where a pointer sees it once the slide has slid in: Playwright's own scroll into view
  // would scroll the snapping track under the link and hand the billboard back to slide 1.
  await expect(ours).toBeInViewport({ ratio: 1 });
  const open = await ours.getByRole("link", { name: "Open" }).boundingBox();
  if (open === null) throw new Error("no Open link");

  // when / then
  await groupsDuring(
    page,
    () => page.mouse.click(open.x + open.width / 2, open.y + open.height / 2),
    cardGroups,
  );
  await expect(heroHeading(page)).toBeVisible();

  // when / then - the lobby comes back on the slide it left from, and the morph lands there
  await groupsDuring(page, () => page.goBack(), cardGroups);
  await expect(ours).not.toHaveAttribute("inert");
});

test("joining from a lobby threshold plays the welcome and never morphs", async ({
  page,
}) => {
  // given
  await recordMorphs(page);
  await stubReads(page, {
    ...shellOfCommunity,
    [`/axonia/communities/${SLUG}`]: pictured,
    [`/axonia/communities/${SLUG}/members`]: { ...member, role: "MEMBER" },
  });
  await stubLobby(page, {
    billboard: [],
    shelves: [{ key: "EXPLORE", items: [lobbyItem(pictured)] }],
  });
  await stubImages(page);
  await page.goto("/communities");
  const stage = page.locator("[data-ceremony-stage]");
  await page.getByRole("button", { name: community.name }).click();

  // when / then
  await noGroupsDuring(
    page,
    () =>
      page
        .getByRole("dialog", { name: community.name })
        .getByRole("button", { name: "Join" })
        .click(),
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
