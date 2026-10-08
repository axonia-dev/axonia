import type { Page } from "@playwright/test";
import { expect, test } from "@playwright/test";

import { FIRST_MARK, SLUG, stubbedMap, unstubbed } from "./expertise-map-data";
import { paintedColours } from "./painted-colours";

/**
 * Opens a mark the way a keyboard does. The overlay's buttons are deliberately deaf to the pointer - a pointer is
 * served by the drawing itself, which picks the nearest mark rather than whichever one got a 24px button - so a
 * `click()` on one would fail its own actionability check.
 */
async function openByKeyboard(page: Page, name: RegExp) {
  const target = page.getByRole("button", { name }).first();
  await target.focus();
  await target.press("Enter");
}

test.beforeEach(stubbedMap);

test("the terrain paints, and a mark opens its contribution", async ({
  page,
}) => {
  const errors: string[] = [];
  page.on("pageerror", (e) => errors.push(e.message));
  // The API surface only: the preview server serves no /telemetry route, which is the edge's job in production.
  page.on("response", (r) => {
    const failed = r.status() >= 400 && r.url().includes("/axonia/");
    if (failed) errors.push(`${r.status()} ${r.url()}`);
  });

  await page.goto(`/communities/${SLUG}/expertise?view=map`);

  const canvas = page.locator("canvas").first();
  await expect(canvas).toBeVisible();

  // A painted canvas, not an empty one. The pixels are read from a screenshot rather than from the canvas
  // itself: without preserveDrawingBuffer, which costs performance for nothing, the WebGL buffer is cleared
  // once the frame is composited and drawImage would copy a blank one. This is the only assertion in the whole
  // suite that a GPU ran, since every unit test mocks three.js.
  await expect
    .poll(async () => paintedColours(await canvas.screenshot(), page), {
      timeout: 15_000,
    })
    .toBeGreaterThan(1);

  await openByKeyboard(page, FIRST_MARK);

  const strip = page.getByTestId("contribution-strip");
  await expect(strip).toBeVisible();
  await expect(strip.getByText("Ada Lovelace")).toBeVisible();

  expect([...unstubbed]).toEqual([]);
  expect(errors).toEqual([]);
});

test("every kind of contribution is reachable by keyboard, not just the talks", async ({
  page,
}) => {
  await page.goto(`/communities/${SLUG}/expertise?view=map`);
  await expect(page.locator("canvas").first()).toBeVisible();

  // Only a talk has a reach, so a budget spent on reach would hand every button to the talks and
  // leave the other three kinds unreachable however few of them there were.
  for (const kind of ["a talk", "an artifact", "a topic", "an action item"]) {
    await expect(
      page.getByRole("button", { name: new RegExp(`, ${kind} by `) }).first(),
    ).toBeAttached();
  }
});

test("globe and flat both render, and so does the stream", async ({ page }) => {
  await page.goto(`/communities/${SLUG}/expertise?view=map`);
  await expect(page.locator("canvas").first()).toBeVisible();

  await page.getByRole("radio", { name: "Flat" }).click();
  await expect(page.locator("canvas").first()).toBeVisible();

  await page.getByRole("radio", { name: "Over time" }).click();
  await expect(page.locator("canvas").first()).toBeVisible();

  // The stream draws the same marks, so it gets the same buttons and the same strip.
  await openByKeyboard(page, FIRST_MARK);
  await expect(page.getByTestId("contribution-strip")).toBeVisible();
});

/**
 * The pointer is served by the drawing, not by the overlay, so this is the only thing in either suite that proves
 * a click on the picture opens anything at all. The overlay's own button says where the mark is; the press lands
 * on the canvas under it.
 */
test("a click on the drawing opens the mark under it, and open ground puts it away", async ({
  page,
}) => {
  await page.goto(`/communities/${SLUG}/expertise?view=map`);
  await expect(page.locator("canvas").first()).toBeVisible();

  const target = page.getByRole("button", { name: FIRST_MARK }).first();
  const at = (await target.boundingBox())!;
  await page.mouse.click(at.x + at.width / 2, at.y + at.height / 2);

  const strip = page.getByTestId("contribution-strip");
  await expect(strip).toBeVisible();

  const canvas = (await page.locator("canvas").first().boundingBox())!;
  await page.mouse.click(canvas.x + 6, canvas.y + 6);

  await expect(strip).toBeHidden();
});

test("zoom is reachable without a mouse, and a bare wheel over the drawing zooms it", async ({
  page,
}) => {
  await page.goto(`/communities/${SLUG}/expertise?view=map`);
  const canvas = page.locator("canvas").first();
  await expect(canvas).toBeVisible();

  await page.getByRole("button", { name: "Zoom in" }).click();
  await expect(canvas).toBeVisible();

  // The drawing takes a share of the viewport rather than sitting in a card the page scrolls past, so a wheel
  // over it is a wheel over the map and there is no modifier to discover. It must not also scroll the page out
  // from under the reader.
  await page.evaluate(() => {
    const seen: boolean[] = [];
    (globalThis as unknown as { seen: boolean[] }).seen = seen;
    globalThis.addEventListener(
      "wheel",
      (event) => seen.push(event.defaultPrevented),
      { passive: true },
    );
  });

  const box = (await canvas.boundingBox())!;
  await page.mouse.move(box.x + box.width / 2, box.y + box.height / 2);
  await page.mouse.wheel(0, 400);
  await expect
    .poll(() =>
      page.evaluate(() => (globalThis as unknown as { seen: boolean[] }).seen),
    )
    .toEqual([true]);
});

/**
 * The flat reading and the stream have no far side to turn to; what they have is more of themselves than the frame
 * holds once a reader has zoomed in. Both shipped with no way to reach any of it.
 */
test("every reading can be zoomed and carried about", async ({ page }) => {
  await page.goto(`/communities/${SLUG}/expertise?view=map`);
  const canvas = page.locator("canvas").first();
  await expect(canvas).toBeVisible();

  for (const reading of ["Flat", "Over time"]) {
    await page.getByRole("radio", { name: reading }).click();
    await expect(page.locator("canvas").first()).toBeVisible();

    const box = (await page.locator("canvas").first().boundingBox())!;
    const cx = box.x + box.width / 2;
    const cy = box.y + box.height / 2;
    await page.mouse.move(cx, cy);
    // Each click is one zoom step whatever its delta, and three already push a sixth of the picture out of the frame
    // on every side: room enough for the drag to carry it. Every input event waits on a frame of software WebGL, so
    // the gesture is as few events as proves the point and no more.
    for (let click = 0; click < 3; click++) await page.mouse.wheel(0, -120);

    const before = await page.locator("canvas").first().screenshot();
    await page.mouse.down();
    await page.mouse.move(cx - 168, cy, { steps: 4 });
    await page.mouse.up();

    await expect
      .poll(async () =>
        (await page.locator("canvas").first().screenshot()).equals(before),
      )
      .toBe(false);
  }
});

test("the detail strip stays inside the viewport", async ({ page }) => {
  await page.goto(`/communities/${SLUG}/expertise?view=map`);
  await expect(page.locator("canvas").first()).toBeVisible();

  await openByKeyboard(page, FIRST_MARK);

  // In normal flow under a tall drawing the strip starts below the fold, so selecting a mark brings it into
  // view. Nothing in flow can slide under the browser's own chrome, which is the property the flow buys.
  const strip = page.getByTestId("contribution-strip");
  // Wholly in view, not merely touching it: the strip arrives by a smooth scroll, and its box read while it is still
  // travelling lies below the fold.
  await expect(strip).toBeInViewport({ ratio: 1 });

  const box = (await strip.boundingBox())!;
  const viewport = page.viewportSize()!;

  expect(box.y).toBeGreaterThanOrEqual(0);
  expect(box.y + box.height).toBeLessThanOrEqual(viewport.height + 1);
});

/** A literal in three.js's own WebGL renderer, which survives minification because it is a string. */
const THREE_JS = "Error creating WebGL context";

test("a reader who only searches never fetches the renderer", async ({
  page,
  request,
}) => {
  const scripts = new Set<string>();
  page.on("response", (response) => {
    if (response.request().resourceType() === "script") {
      scripts.add(response.url());
    }
  });

  await page.goto(`/communities/${SLUG}/expertise?view=search`);
  await expect(page.getByRole("heading", { level: 1 })).toBeVisible();

  const onSearch = [...scripts];
  expect(onSearch.length).toBeGreaterThan(0);
  for (const url of onSearch) {
    const body = await (await request.get(url)).text();
    expect(body, `${url} carries three.js`).not.toContain(THREE_JS);
  }

  await page.goto(`/communities/${SLUG}/expertise?view=map`);
  await expect(page.locator("canvas").first()).toBeVisible();

  // And it is genuinely the map that brings it: a chunk nobody ever loads would pass the half above.
  const added = [...scripts].filter((url) => !onSearch.includes(url));
  const bodies = await Promise.all(
    added.map(async (url) => (await request.get(url)).text()),
  );
  expect(bodies.some((body) => body.includes(THREE_JS))).toBe(true);
});
