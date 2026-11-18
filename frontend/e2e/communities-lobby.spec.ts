import type { Page } from "@playwright/test";
import { expect, test } from "@playwright/test";

import { member, shellOfCommunity, SLUG, stubReads } from "./community-home";
import { lobbyItem, lobbyItems, stubLobby } from "./lobby-support";

const inTwoDays = new Date(Date.now() + 2 * 86_400_000).toISOString();

const featured = [
  lobbyItem({ id: 11, slug: "platform-guild", name: "Platform Guild" }),
  lobbyItem({ id: 12, slug: "data-guild", name: "Data Guild" }),
  lobbyItem({ id: 13, slug: "design-circle", name: "Design Circle" }),
];

const kafka = lobbyItem({
  id: 31,
  slug: "kafka-circle",
  name: "Kafka Circle",
  nextSessionThisWeek: true,
  nextSession: {
    title: "Exactly once, really",
    scheduledAt: inTwoDays,
    hasTime: true,
    zone: "Europe/Zurich",
  },
});

const joinable = lobbyItem({ description: "Where the backend people meet" });

const lobby = {
  billboard: featured,
  shelves: [
    {
      key: "JOINED",
      items: [
        lobbyItem({
          id: 21,
          slug: "frontend-guild",
          name: "Frontend Guild",
          role: "MEMBER",
        }),
      ],
    },
    { key: "THIS_WEEK", items: [kafka] },
    { key: "GROWING", items: lobbyItems(41, 3, { joinedLast30Days: 5 }) },
    { key: "OTHERS", items: [joinable, ...lobbyItems(51, 5)] },
  ],
};

async function visitLobby(page: Page): Promise<void> {
  await stubReads(page, {
    ...shellOfCommunity,
    [`/axonia/communities/${SLUG}/members`]: { ...member, role: "MEMBER" },
  });
  await stubLobby(page, lobby, [kafka]);
  await page.goto("/communities");
  await expect(billboard(page)).toBeVisible();
}

const billboard = (page: Page) =>
  page.getByRole("region", { name: "Featured communities" });

/** The slide on show: the carousel makes every other slide inert. */
const activeSlide = (page: Page) =>
  billboard(page)
    .locator('[aria-roledescription="slide"]:not([inert])')
    .getAttribute("aria-label");

/** Somewhere off the billboard, so hovering it does not hold the rotation. */
async function pointElsewhere(page: Page): Promise<void> {
  await page.mouse.move(5, (page.viewportSize()?.height ?? 800) - 5);
}

test.describe("at a desk", () => {
  test.use({ viewport: { width: 1440, height: 900 } });

  test("the billboard moves with its arrows and dots", async ({ page }) => {
    // given
    await visitLobby(page);
    await expect.poll(() => activeSlide(page)).toBe("Platform Guild, 1 of 3");

    // when / then
    await billboard(page).getByRole("button", { name: "Next slide" }).click();
    await expect.poll(() => activeSlide(page)).toBe("Data Guild, 2 of 3");

    // when / then
    await billboard(page)
      .getByRole("button", { name: "Previous slide" })
      .click();
    await expect.poll(() => activeSlide(page)).toBe("Platform Guild, 1 of 3");

    // when / then
    await billboard(page)
      .getByRole("button", { name: "Show Design Circle" })
      .click();
    await expect.poll(() => activeSlide(page)).toBe("Design Circle, 3 of 3");
  });

  test("a sideways drag moves the billboard on", async ({ page }) => {
    // given
    await visitLobby(page);
    const box = await billboard(page).boundingBox();
    if (box === null) throw new Error("no billboard");
    const y = box.y + 60;

    // when
    await page.mouse.move(box.x + box.width * 0.8, y);
    await page.mouse.down();
    await page.mouse.move(box.x + box.width * 0.3, y, { steps: 12 });
    await page.mouse.up();

    // then
    await expect.poll(() => activeSlide(page)).toBe("Data Guild, 2 of 3");
  });

  test("pause stops the rotation", async ({ page }) => {
    test.setTimeout(60_000);

    // given - it rotates on its own
    await visitLobby(page);
    await pointElsewhere(page);
    await expect
      .poll(() => activeSlide(page), { timeout: 10_000 })
      .toBe("Data Guild, 2 of 3");

    // when
    await billboard(page).getByRole("button", { name: "Pause" }).click();
    await pointElsewhere(page);

    // then - a whole interval and more passes without the slide changing
    await expect(
      expect
        .poll(() => activeSlide(page), { timeout: 9_000 })
        .not.toBe("Data Guild, 2 of 3"),
    ).rejects.toThrow();
    await expect(
      billboard(page).getByRole("button", { name: "Play" }),
    ).toBeVisible();
  });

  test("the shelves follow the billboard, each under its own heading", async ({
    page,
  }) => {
    // when
    await visitLobby(page);

    // then
    for (const [shelf, card] of [
      ["Joined", "Frontend Guild"],
      ["Sessions this week", "Kafka Circle"],
      ["Growing fast", "Community 41"],
      ["Every other community", "Community 51"],
    ]) {
      await expect(
        page
          .getByRole("region", { name: shelf })
          .getByRole("article", { name: card }),
      ).toBeVisible();
    }
  });

  test("a joined card opens its community in one click, with no threshold", async ({
    page,
  }) => {
    // given
    await visitLobby(page);

    // when
    await page
      .getByRole("region", { name: "Joined" })
      .getByRole("link", { name: "Frontend Guild" })
      .click();

    // then
    await expect(page).toHaveURL("/communities/frontend-guild");
    await expect(page.getByRole("dialog")).toHaveCount(0);
  });

  test("a card opens its threshold, and Escape hands focus back to the card", async ({
    page,
  }) => {
    // given
    await visitLobby(page);
    const card = page
      .getByRole("region", { name: "Growing fast" })
      .getByRole("button", { name: "Community 42" });

    // when
    await card.click();

    // then
    await expect(
      page.getByRole("dialog", { name: "Community 42" }),
    ).toBeVisible();

    // when
    await page.keyboard.press("Escape");

    // then
    await expect(page.getByRole("dialog")).toHaveCount(0);
    await expect(card).toBeFocused();
  });

  test("a search trades the billboard and shelves for its matches", async ({
    page,
  }) => {
    // given
    await visitLobby(page);

    // when
    await page
      .getByRole("searchbox", { name: "Search communities" })
      .fill("kafka");

    // then
    await expect(
      page
        .getByRole("region", { name: "Matches" })
        .getByRole("button", { name: "Kafka Circle" }),
    ).toBeVisible();
    await expect(billboard(page)).toHaveCount(0);
    await expect(page.getByRole("region", { name: "Joined" })).toHaveCount(0);
  });

  test("Join on a threshold joins, then departs to the community's welcome", async ({
    page,
  }) => {
    // given
    await visitLobby(page);
    await page.getByRole("button", { name: joinable.name }).click();
    const dialog = page.getByRole("dialog", { name: joinable.name });
    const joined = page.waitForRequest(
      (request) =>
        request.method() === "POST" &&
        new URL(request.url()).pathname ===
          `/axonia/communities/${SLUG}/members`,
    );

    // when
    await dialog.getByRole("button", { name: "Join" }).click();

    // then
    await joined;
    await expect(page.locator("[data-ceremony-stage]")).toBeVisible();
    await expect(page).toHaveURL(`/communities/${SLUG}`);
  });
});

test.describe("on a phone", () => {
  test.use({
    viewport: { width: 412, height: 892 },
    isMobile: true,
    hasTouch: true,
  });

  test("the lobby never scrolls sideways", async ({ page }) => {
    // given
    await visitLobby(page);
    await expect(
      page.getByRole("region", { name: "Every other community" }),
    ).toBeVisible();

    // when
    const width = await page.locator("main").evaluate((main) => ({
      scroll: main.scrollWidth,
      client: main.clientWidth,
    }));

    // then
    expect(width.scroll).toBeLessThanOrEqual(width.client);
  });
});
