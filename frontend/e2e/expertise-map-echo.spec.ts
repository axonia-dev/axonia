import type { Locator, Page } from "@playwright/test";
import { expect, test } from "@playwright/test";

import { SLUG, stubbedMap, unstubbed } from "./expertise-map-data";
import { differingPixels } from "./painted-colours";

const URL = `/communities/${SLUG}/expertise?view=map`;
// Frames of a live canvas never match exactly; below this many differing pixels two pictures are the same one.
const SAME = 40;
const MID_ECHO_MS = 750;
const SETTLE_TIMEOUT_MS = 15_000;

// Tall enough that the whole canvas fits the viewport, so a screenshot of it never scrolls the page mid-echo.
const VIEWPORT = { width: 1280, height: 1000 };
test.use({ viewport: VIEWPORT });

async function still(page: Page, canvas: Locator): Promise<Buffer> {
  let last = await canvas.screenshot();
  let stable = 0;
  await expect
    .poll(
      async () => {
        const next = await canvas.screenshot();
        stable =
          (await differingPixels(next, last, page)) < SAME ? stable + 1 : 0;
        last = next;
        return stable;
      },
      { timeout: SETTLE_TIMEOUT_MS },
    )
    .toBeGreaterThanOrEqual(2);
  return last;
}

async function open(page: Page) {
  await page.goto(URL);
  const canvas = page.locator("canvas").first();
  await expect(canvas).toBeVisible();
  await still(page, canvas);
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
  const calm = await browser.newPage({
    reducedMotion: "reduce",
    viewport: VIEWPORT,
  });
  await stubbedMap({ page: calm });
  const calmCanvas = await open(calm);
  await type(calm, "kafka");
  await expect(calm.getByText("Who knows kafka")).toBeVisible();
  const reduced = await still(calm, calmCanvas);
  await calm.close();

  await page.clock.install();
  const canvas = await open(page);
  const before = await canvas.screenshot();

  // A software-rendered screenshot takes a second or more, longer than an echo's opening, so the clock is held still
  // mid-echo for the shot rather than raced.
  await page.clock.pauseAt(await page.evaluate(() => Date.now() + 1000));
  await type(page, "kafka");
  await page.clock.runFor(MID_ECHO_MS);
  const during = await canvas.screenshot();
  await page.clock.resume();

  await expect
    .poll(
      async () => differingPixels(await canvas.screenshot(), reduced, page),
      {
        timeout: SETTLE_TIMEOUT_MS,
      },
    )
    .toBeLessThan(SAME);

  expect(await differingPixels(during, before, page)).toBeGreaterThan(SAME);
  expect(await differingPixels(during, reduced, page)).toBeGreaterThan(SAME);

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

// Stacked, the readings sit above the map on a phone and under it on a tablet: either way the answer to a term is
// listed under the drawing, in sight of the box, and nothing above the box moves while the term is typed.
for (const width of [412, 768]) {
  test(`at ${width}px the answer to a term shows under the drawing, and the box stays put`, async ({
    page,
  }) => {
    await page.setViewportSize({ width, height: 892 });
    const canvas = await open(page);
    // Where a reader using the map holds it: the whole drawing in sight.
    await canvas.evaluate((el) => el.scrollIntoView({ block: "center" }));
    const search = page.getByLabel("Find on the map");
    const before = (await search.boundingBox())!.y;

    await type(page, "kafka");
    const heading = page.getByRole("heading", { name: "Who knows kafka" });
    await expect(heading).toBeInViewport();
    expect((await search.boundingBox())!.y).toBe(before);
    expect((await heading.boundingBox())!.y).toBeGreaterThan(
      (await canvas.boundingBox())!.y,
    );
    expect((await canvas.boundingBox())!.width).toBeGreaterThan(width / 2);
  });
}
