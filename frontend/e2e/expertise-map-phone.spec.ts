import type { Locator, Page } from "@playwright/test";
import { expect, test } from "@playwright/test";

import { FIRST_MARK, SLUG, stubbedMap } from "./expertise-map-data";
import { paintedColours } from "./painted-colours";

// A phone, emulated as one: a desktop window stops near 500px, and touch-action only means anything to real touch.
test.use({
  viewport: { width: 412, height: 892 },
  isMobile: true,
  hasTouch: true,
});

type Fingers = { x: number; y: number }[];

/**
 * Real touch input, the fingers moved in small steps from one frame to the next. Playwright's touchscreen only
 * taps, and a pinch or a swipe has to reach the browser's own gesture handling for `touch-action` to mean anything.
 */
async function touch(page: Page, frames: Fingers[]) {
  const cdp = await page.context().newCDPSession(page);
  const send = (
    type: "touchStart" | "touchMove" | "touchEnd",
    touchPoints: Fingers,
  ) => cdp.send("Input.dispatchTouchEvent", { type, touchPoints });
  await send("touchStart", frames[0]);
  for (let frame = 1; frame < frames.length; frame++) {
    const [from, to] = [frames[frame - 1], frames[frame]];
    for (let step = 1; step <= 8; step++) {
      await send(
        "touchMove",
        to.map((end, finger) => ({
          x: from[finger].x + ((end.x - from[finger].x) * step) / 8,
          y: from[finger].y + ((end.y - from[finger].y) * step) / 8,
        })),
      );
    }
  }
  await send("touchEnd", []);
}

/**
 * Waits for the drawing to stop matching `before`, and hands back the picture it became. The map moves only when a
 * gesture moves it, with no easing to settle, so the frame that differs is the one to compare the next gesture with.
 */
async function changedFrom(canvas: Locator, before: Buffer): Promise<Buffer> {
  let now = before;
  await expect
    .poll(async () => {
      now = await canvas.screenshot();
      return now.equals(before);
    })
    .toBe(false);
  return now;
}

test.beforeEach(stubbedMap);

test("the map is drawn under the readings, and nothing overflows", async ({
  page,
}) => {
  await page.goto(`/communities/${SLUG}/expertise?view=map`);

  const canvas = page.locator("canvas").first();
  await canvas.scrollIntoViewIfNeeded();
  await expect(canvas).toBeVisible();
  await expect
    .poll(async () => paintedColours(await canvas.screenshot(), page), {
      timeout: 15_000,
    })
    .toBeGreaterThan(1);

  const panel = (await page
    .getByRole("complementary", { name: "Topic readings" })
    .boundingBox())!;
  const drawing = (await canvas.boundingBox())!;
  expect(drawing.y).toBeGreaterThanOrEqual(panel.y + panel.height);

  const overflow = await page.evaluate(() => {
    const main = document.querySelector("main");
    return main === null ? 0 : main.scrollWidth - main.clientWidth;
  });
  expect(overflow).toBeLessThanOrEqual(0);
});

test("a tap on the drawing opens the mark under it", async ({ page }) => {
  await page.goto(`/communities/${SLUG}/expertise?view=map`);
  const canvas = page.locator("canvas").first();
  await canvas.scrollIntoViewIfNeeded();
  await expect(canvas).toBeVisible();

  const target = page.getByRole("button", { name: FIRST_MARK }).first();
  const at = (await target.boundingBox())!;
  await page.touchscreen.tap(at.x + at.width / 2, at.y + at.height / 2);

  const strip = page.getByTestId("contribution-strip");
  await expect(strip).toBeVisible();
  await expect(strip.getByText("Ada Lovelace")).toBeVisible();
});

/** A picture with nowhere to go hands a vertical swipe back to the page, or half a phone screen scrolls nothing. */
test("a swipe on the flat reading at rest scrolls the page", async ({
  page,
}) => {
  await page.goto(`/communities/${SLUG}/expertise?view=map`);
  await page.getByRole("radio", { name: "Flat" }).click();
  const canvas = page.locator("canvas").first();
  await expect(canvas).toBeVisible();
  const main = page.locator("main");
  await main.evaluate((m) => m.scrollTo(0, 0));

  // Kept inside the part of the canvas that is on screen: the tab bar is fixed over the bottom of the page.
  const box = (await canvas.boundingBox())!;
  const x = box.x + box.width / 2;
  const y = box.y + 60;
  await touch(page, [[{ x, y: y + 160 }], [{ x, y }]]);

  await expect.poll(() => main.evaluate((m) => m.scrollTop)).toBeGreaterThan(0);
});

test("two fingers pinch the picture in, and a finger on the globe turns it", async ({
  page,
}) => {
  await page.goto(`/communities/${SLUG}/expertise?view=map`);
  const canvas = page.locator("canvas").first();
  // The screenshot scrolls the canvas into view itself, so the page is where the gestures leave it from here on.
  await expect
    .poll(async () => paintedColours(await canvas.screenshot(), page), {
      timeout: 15_000,
    })
    .toBeGreaterThan(1);
  const main = page.locator("main");
  const scrolled = await main.evaluate((m) => m.scrollTop);
  const box = (await canvas.boundingBox())!;
  const cx = box.x + box.width / 2;
  const cy = box.y + box.height / 2;

  const atRest = await canvas.screenshot();
  // One stretch of the fingers, not several: every touch event waits on a frame of software WebGL, and a pinch is
  // the ratio of the spreads however many moves it took to get there.
  await touch(page, [
    [
      { x: cx - 20, y: cy },
      { x: cx + 20, y: cy },
    ],
    [
      { x: cx - 120, y: cy },
      { x: cx + 120, y: cy },
    ],
  ]);
  const pinched = await changedFrom(canvas, atRest);

  await touch(page, [[{ x: cx, y: cy + 60 }], [{ x: cx, y: cy - 60 }]]);
  await changedFrom(canvas, pinched);
  expect(await main.evaluate((m) => m.scrollTop)).toBe(scrolled);
});

/**
 * At rest the flat reading hands a vertical swipe to the page, and a pinch is the only touch way out of that state.
 * The fingers drift upwards as real ones do, so a browser that scrolled on their midpoint would cancel the pinch.
 */
test("a pinch on the flat reading at rest zooms it rather than scrolling the page", async ({
  page,
}) => {
  await page.goto(`/communities/${SLUG}/expertise?view=map`);
  await page.getByRole("radio", { name: "Flat" }).click();
  const canvas = page.locator("canvas").first();
  await expect(canvas).toBeVisible();
  const main = page.locator("main");
  // Taken first, because a screenshot scrolls the canvas into view and the page must be still before the pinch.
  const atRest = await canvas.screenshot();
  const scrolled = await main.evaluate((m) => m.scrollTop);

  const box = (await canvas.boundingBox())!;
  const cx = box.x + box.width / 2;
  const cy = box.y + 120;
  await touch(page, [
    [
      { x: cx - 20, y: cy },
      { x: cx + 20, y: cy },
    ],
    [
      { x: cx - 70, y: cy - 25 },
      { x: cx + 70, y: cy - 15 },
    ],
    [
      { x: cx - 130, y: cy - 50 },
      { x: cx + 130, y: cy - 30 },
    ],
  ]);

  await expect
    .poll(async () => (await canvas.screenshot()).equals(atRest))
    .toBe(false);
  expect(await main.evaluate((m) => m.scrollTop)).toBe(scrolled);
});
