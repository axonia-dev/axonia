import type { Page } from "@playwright/test";
import { expect, test } from "@playwright/test";

import {
  activityTrace,
  community,
  shellOfCommunity,
  SLUG,
  stubReads,
} from "./community-home";

async function visitAliveCommunity(page: Page): Promise<void> {
  await stubReads(page, {
    ...shellOfCommunity,
    [`/axonia/communities/${SLUG}/activity-trace`]: activityTrace({
      sessionsHeld: [0, 12],
      libraryAdditions: [3],
      joins: [1, 40],
    }),
  });
  await page.goto(`/communities/${SLUG}`);
}

test("the community home carries a living pulse", async ({ page }) => {
  // given - a community that held a session today
  await visitAliveCommunity(page);

  // then
  const field = page.locator("[data-ambient-field]");
  await expect(field).toHaveAttribute("data-painted", "true");
  await expect(field).toHaveAttribute("aria-hidden", "true");
  await expect(
    page.getByRole("heading", { name: community.name }),
  ).toBeVisible();
});

test("reduced motion still shows the pulse, standing still", async ({
  page,
}) => {
  // given
  await page.emulateMedia({ reducedMotion: "reduce" });

  // when
  await visitAliveCommunity(page);

  // then
  await expect(page.locator("[data-ambient-field]")).toHaveAttribute(
    "data-painted",
    "true",
  );
});

test("a community without a trace keeps its card and shows no field", async ({
  page,
}) => {
  // given - the trace read fails
  await stubReads(page, shellOfCommunity);

  // when
  await page.goto(`/communities/${SLUG}`);

  // then
  await expect(
    page.getByRole("heading", { name: community.name }),
  ).toBeVisible();
  await expect(page.locator("[data-ambient-field]")).toHaveCount(0);
});
