import { expect, type Page, test } from "@playwright/test";

import { shellOfCommunity, SLUG, stubReads } from "./community-home";
import { overflowing } from "./overflow";

// A phone, emulated as a phone - see mobile-layout.spec.ts for why this is the one width that matters.
test.use({
  viewport: { width: 412, height: 892 },
  isMobile: true,
  hasTouch: true,
});

/** Shelf cards with anything drawn past their own edges, which `overflowing` misses while the
 *  spill stays inside `main`. */
function spillingCards(page: Page): Promise<string[]> {
  return page.evaluate(() =>
    [...document.querySelectorAll("button[aria-label*=' open to questions']")]
      .filter((card) => {
        const box = card.getBoundingClientRect();
        return [...card.querySelectorAll("*")].some((element) => {
          const inner = element.getBoundingClientRect();
          return inner.width > 0 && inner.right > box.right + 0.5;
        });
      })
      .map((card) => card.getAttribute("aria-label") ?? ""),
  );
}

test("the open to questions shelf fits a phone, four faces and New included", async ({
  page: browser,
}) => {
  // given - a fresh area with more people open on it than a card draws
  const fresh = new Date().toISOString();
  await stubReads(browser, {
    ...shellOfCommunity,
    [`/axonia/communities/${SLUG}/expertise/open`]: [
      {
        canonicalTag: "observability",
        latestDeclaredAt: fresh,
        people: ["lea", "tom", "ana", "bo", "cy"].map((userId) => ({
          userId,
          displayName: userId,
          avatarUrl: null,
          declaredAt: fresh,
        })),
      },
    ],
  });

  // when
  await browser.goto(`/communities/${SLUG}/expertise?view=search`);

  // then
  await expect(
    browser.getByRole("button", {
      name: "observability, 5 people open to questions, new",
    }),
  ).toBeVisible();
  expect(await spillingCards(browser)).toEqual([]);
  expect(await overflowing(browser)).toEqual([]);
});
