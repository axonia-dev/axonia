import type { Locator, Page } from "@playwright/test";
import { expect, test } from "@playwright/test";

import {
  activityTrace,
  community,
  shellOfCommunity,
  SLUG,
  stubReads,
} from "./community-home";
import { differingPixels } from "./painted-colours";

const TRACE = `/axonia/communities/${SLUG}/activity-trace`;
const HERO_PHOTO = "/hero-photo.png";
// A 16x9 dark gradient, so the light ink drawn over a photo has something to show against.
const PHOTO_PNG = Buffer.from(
  "iVBORw0KGgoAAAANSUhEUgAAABAAAAAJCAIAAAC0SDtlAAABVklEQVR42gXBQaeFQBQA4H5JixYtWsziLDJanMUYwzuu7hg6Mo1oxDBERETE5fJ4f/p9X1GCqeCnhlcDbwEOYGhh7GBCmBUsBlaC1EN2sDHsHo6ilD+VfNXy3Ugn5ABybOXUyRnlouRqZCKZe7k5ubM8vDyLEl8Vvmt0DQ4CR8CpxbnDBXFVmAxmwq3H3eHBeHq8ilK/K+1qPTR6FHoCPbd66fSKOimdjd5I770+nD5ZX17fRUmuoqGmsaFJ0Ay0tLR2lJCyos3QTnT0dDq6mG5PT1HaobJjbafGzsIuYNfWps5mtJuyu7EH2bO3l7M328fbT1HyWPFU89zwIngFTi3njjfkXfFh+CS+er4dP8wfz9+iDFMV5josTVhFSBByG7Yu7BgOFU4TLgp3Hx4XPhy+PvwWZZyruNRxbWISMUPc2rh38cB4qniZeFN8+vhx8cvx18e/fwu1hil4cYZvAAAAAElFTkSuQmCC",
  "base64",
);

async function visitAliveCommunity(
  page: Page,
  heroUrl: string | null = null,
): Promise<void> {
  await stubReads(page, {
    ...shellOfCommunity,
    [`/axonia/communities/${SLUG}`]: { ...community, heroUrl },
    // Busy enough that some motes always stand below the banner, whatever moment the still shows.
    [TRACE]: activityTrace({
      sessionsHeld: Array.from({ length: 30 }, (_, day) => day),
      libraryAdditions: [3, 20],
      joins: [1, 40],
    }),
  });
  await page.route(`**${HERO_PHOTO}`, (route) =>
    route.fulfill({ contentType: "image/png", body: PHOTO_PNG }),
  );
  await page.goto(`/communities/${SLUG}`);
}

/** A still frame, fully faded in, so two screenshots differ only by what the test changes. */
async function stillField(page: Page): Promise<Locator> {
  await page.emulateMedia({ reducedMotion: "reduce" });
  const field = page.locator("[data-ambient-field]");
  await expect(field).toHaveAttribute("data-painted", "true");
  await expect(field).toHaveCSS("opacity", "1");
  return field;
}

// A buried canvas still bleeds through the card's anti-aliased rounded edge, so the comparison
// stays clear of it; and its compositing layer alone nudges text and gradients by a few levels,
// which `differingPixels` ignores. A field that truly shows moves hundreds of pixels.
const SHOWS = 20;
const EDGE = 12;

async function inside(region: Locator) {
  const box = await region.boundingBox();
  if (box === null) throw new Error("the region is not on screen");
  return {
    x: box.x + EDGE,
    y: box.y + EDGE,
    width: box.width - 2 * EDGE,
    height: box.height - 2 * EDGE,
  };
}

/** How many pixels inside `region` change, beyond antialiasing noise, when the field is hidden. */
async function fieldPixelsIn(field: Locator, region: Locator) {
  const page = region.page();
  const clip = await inside(region);
  const shown = await page.screenshot({ clip, animations: "disabled" });
  await field.evaluate((canvas) => (canvas.style.visibility = "hidden"));
  const hidden = await page.screenshot({ clip, animations: "disabled" });
  await field.evaluate((canvas) => (canvas.style.visibility = ""));
  return differingPixels(shown, hidden, page);
}

/** The canvas's own pixels once `frames` more animation frames have run. */
function bitmapAfter(field: Locator, frames: number): Promise<string> {
  return field.evaluate(
    (canvas: HTMLCanvasElement, count) =>
      new Promise<string>((done) => {
        const step = (left: number) =>
          left === 0
            ? done(canvas.toDataURL())
            : requestAnimationFrame(() => step(left - 1));
        step(count);
      }),
    frames,
  );
}

const part = (page: Page, name: string) =>
  page.locator(`[data-ceremony-part="${name}"]`).first();

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

test("the pulse reaches the screen on a card without a photo, behind the name too", async ({
  page,
}) => {
  // given
  await visitAliveCommunity(page);

  // when
  const field = await stillField(page);

  // then
  expect(await fieldPixelsIn(field, part(page, "surface"))).toBeGreaterThan(
    SHOWS,
  );
  expect(await fieldPixelsIn(field, part(page, "identity"))).toBeGreaterThan(
    SHOWS,
  );
});

test("the pulse reaches the screen over a hero photo and below it", async ({
  page,
}) => {
  // given
  await visitAliveCommunity(page, HERO_PHOTO);
  await expect(part(page, "banner").locator("img")).toHaveJSProperty(
    "complete",
    true,
  );

  // when
  const field = await stillField(page);

  // then
  expect(await fieldPixelsIn(field, part(page, "banner"))).toBeGreaterThan(
    SHOWS,
  );
  expect(await fieldPixelsIn(field, part(page, "identity"))).toBeGreaterThan(
    SHOWS,
  );
});

test("reduced motion still shows the pulse, standing still", async ({
  page,
}) => {
  // given
  await page.emulateMedia({ reducedMotion: "reduce" });

  // when
  await visitAliveCommunity(page);
  const field = await stillField(page);

  // then
  const first = await bitmapAfter(field, 0);
  expect(await bitmapAfter(field, 30)).toBe(first);
  expect(await fieldPixelsIn(field, part(page, "surface"))).toBeGreaterThan(
    SHOWS,
  );
});

test("a community without a trace keeps its card and shows no field", async ({
  page,
}) => {
  // given - the trace read fails, and its one retry too
  let answered = 0;
  page.on("response", (response) => {
    if (new URL(response.url()).pathname === TRACE) answered += 1;
  });
  await stubReads(page, shellOfCommunity);

  // when
  await page.goto(`/communities/${SLUG}`);
  await expect.poll(() => answered, { timeout: 10_000 }).toBe(2);
  await page.evaluate(
    () =>
      new Promise((done) =>
        requestAnimationFrame(() => requestAnimationFrame(done)),
      ),
  );

  // then
  await expect(
    page.getByRole("heading", { name: community.name }),
  ).toBeVisible();
  await expect(page.locator("[data-ambient-field]")).toHaveCount(0);
});
