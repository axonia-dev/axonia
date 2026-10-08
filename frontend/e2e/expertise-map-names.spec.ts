import { expect, test } from "@playwright/test";

import { SLUG, stubbedMap } from "./expertise-map-data";

test.beforeEach(stubbedMap);

/**
 * The names are laid out against the widths a real font resolves to, and dropped where two of them would collide.
 * jsdom measures every box as zero, so it is the one place in either suite that cannot tell a readable map from one
 * with every word piled on the same spot.
 */
test("the drawing carries the names of the areas it draws", async ({
  page,
}) => {
  await page.goto(`/communities/${SLUG}/expertise?view=map`);
  await expect(page.locator("canvas").first()).toBeVisible();

  const names = page.getByTestId("map-label");
  await expect(names.first()).toBeVisible();

  const boxes = await names.evaluateAll((nodes) =>
    nodes.map((node) => node.getBoundingClientRect()),
  );
  for (const [index, box] of boxes.entries()) {
    expect(box.width).toBeGreaterThan(0);
    for (const other of boxes.slice(index + 1)) {
      const apart =
        Math.abs(box.x - other.x) >= (box.width + other.width) / 2 ||
        Math.abs(box.y - other.y) >= (box.height + other.height) / 2;
      expect(apart).toBe(true);
    }
  }
});

/** Choosing a reading used to change nothing a reader could see on the map beside it. */
test("choosing a reading picks its own areas out of the drawing", async ({
  page,
}) => {
  await page.goto(`/communities/${SLUG}/expertise?view=map`);
  await expect(page.getByTestId("map-label").first()).toBeVisible();

  await page.getByRole("button", { name: /reading/i }).click();
  await page.getByRole("option", { name: "Recently active" }).click();

  await expect(
    page.locator('[data-testid="map-label"][data-lit="false"]').first(),
  ).toBeVisible();
  await expect(
    page.locator('[data-testid="map-label"][data-lit="true"]').first(),
  ).toBeVisible();
});
