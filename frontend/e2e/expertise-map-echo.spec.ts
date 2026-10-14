import type { Page } from "@playwright/test";
import { expect, test } from "@playwright/test";

import { SLUG, stubbedMap, unstubbed } from "./expertise-map-data";
import { differingPixels } from "./painted-colours";

const URL = `/communities/${SLUG}/expertise?view=map`;
// Frames of a live canvas never match exactly; below this many differing pixels two pictures are the same one.
const SAME = 40;
const ECHO_WINDOW_MS = 3500;
const SETTLED_MS = 8000;

// Tall enough that the whole canvas is on screen: an echo only plays on a map that is.
const VIEWPORT = { width: 1280, height: 1000 };
test.use({ viewport: VIEWPORT });

async function open(page: Page) {
  await page.goto(URL);
  const canvas = page.locator("canvas").first();
  await expect(canvas).toBeVisible();
  await page.waitForTimeout(1500);
  return canvas;
}

async function type(page: Page, term: string) {
  await page.getByLabel("Find on the map").fill(term);
}

test.beforeEach(stubbedMap);

test("a term echoes across the map and settles on the reduced-motion picture", async ({
  page,
  browser,
}) => {
  const canvas = await open(page);
  const before = await canvas.screenshot();

  await type(page, "kafka");
  const started = Date.now();
  // A software-rendered screenshot takes a second or more, so a single one at a chosen instant may land after the
  // echo. Shots are taken back to back through the echo's first seconds, and one of them must show it mid-flight.
  const during: Buffer[] = [];
  while (Date.now() - started < ECHO_WINDOW_MS) {
    during.push(await canvas.screenshot());
  }
  await page.waitForTimeout(Math.max(0, SETTLED_MS - (Date.now() - started)));
  const settled = await canvas.screenshot();

  const apart = async (shot: Buffer) =>
    Math.min(
      await differingPixels(shot, before, page),
      await differingPixels(shot, settled, page),
    );
  const gaps = await Promise.all(during.map(apart));
  expect(Math.max(...gaps)).toBeGreaterThan(SAME);

  const calm = await browser.newPage({
    reducedMotion: "reduce",
    viewport: VIEWPORT,
  });
  await stubbedMap({ page: calm });
  const calmCanvas = await open(calm);
  await type(calm, "kafka");
  await calm.waitForTimeout(1000);
  const reduced = await calmCanvas.screenshot();
  expect(await differingPixels(settled, reduced, page)).toBeLessThan(SAME);
  await calm.close();

  expect([...unstubbed]).toEqual([]);
});

test("one matched area lists who knows it, and an author matches none", async ({
  page,
}) => {
  await open(page);

  await type(page, "kafka");
  await expect(page.getByText("Who knows kafka")).toBeVisible();
  for (const name of ["Ada Lovelace", "Grace Hopper", "Edsger Dijkstra"]) {
    await expect(page.getByText(name).first()).toBeVisible();
  }
  await expect(page.getByText("Barbara Liskov")).toBeVisible();
  await expect(
    page.getByRole("button", { name: "Open in Search" }),
  ).toBeVisible();

  await type(page, "grace");
  await expect(page.getByText(/^Who knows /)).toHaveCount(0);
});
