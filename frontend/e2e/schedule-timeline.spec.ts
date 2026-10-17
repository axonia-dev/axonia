import type { Locator, Page } from "@playwright/test";
import { expect, test } from "@playwright/test";

import { SLUG } from "./community-home";
import { overflowing } from "./overflow";
import {
  newest,
  NEXT_TITLE,
  NOW,
  oldest,
  PAUSED_AT,
  stubTimeline,
  type TimelineStubs,
} from "./schedule-timeline-support";

const URL = `/communities/${SLUG}/schedule?view=timeline`;

async function open(
  page: Page,
  stubs: Partial<TimelineStubs> = {},
  { paused = false } = {},
) {
  const wired = { memoryRequests: [], ...stubs };
  await page.clock.install({ time: new Date(NOW) });
  await stubTimeline(page, wired);
  await page.goto(URL);
  await expect(page.getByText("Today", { exact: true })).toBeVisible();
  if (paused) await page.clock.pauseAt(new Date(PAUSED_AT));
  return wired;
}

const main = (page: Page) => page.locator("main");
const slider = (page: Page) =>
  page.getByRole("slider", { name: "Schedule timeline" });
const rowAt = (page: Page, scheduledAt: string) =>
  page.locator(`li[data-at="${Date.parse(scheduledAt)}"]`);
const topOf = (locator: Locator) =>
  locator.evaluate((element) => element.getBoundingClientRect().top);
const readingLine = (page: Page) =>
  main(page).evaluate((element) => {
    const ribbon = element.querySelector("[data-time-ribbon]");
    return (
      element.getBoundingClientRect().top +
      (ribbon?.getBoundingClientRect().height ?? 0) +
      24
    );
  });

async function pressLensAt(page: Page, fraction: number) {
  const box = (await slider(page).boundingBox())!;
  await page.mouse.move(box.x + box.width * fraction, box.y + box.height / 2);
  await page.mouse.down();
  await page.mouse.up();
}

async function expectOpeningPosition(page: Page) {
  const todayLabel = page.getByText("Today", { exact: true });
  const area = (await main(page).boundingBox())!;
  const ratioOf = async () =>
    ((await todayLabel.boundingBox())!.y - area.y) / area.height;
  await expect.poll(ratioOf).toBeLessThanOrEqual(0.42);
  expect(await ratioOf()).toBeGreaterThanOrEqual(0.25);
  const today = (await todayLabel.boundingBox())!;
  const ribbon = (await page.locator("[data-time-ribbon]").boundingBox())!;
  const ribbonBottom = ribbon.y + ribbon.height;
  expect(today.y).toBeGreaterThanOrEqual(ribbonBottom);
  await expect(page.getByRole("link", { name: /^Retro 112/ })).toBeAttached();
  const memoryTop = await topOf(rowAt(page, newest.entry.scheduledAt));
  expect(memoryTop).toBeGreaterThanOrEqual(ribbonBottom);
  expect(memoryTop).toBeLessThan(today.y);
  const controls = (await page
    .getByText("Timeline", { exact: true })
    .first()
    .boundingBox())!;
  const whole = controls.y >= area.y;
  const scrolledAway = controls.y + controls.height <= area.y;
  expect(whole || scrolledAway).toBe(true);
}

test("opens with the Today marker in the upper third of the page", async ({
  page,
}) => {
  // given / when
  await open(page);

  // then
  await expectOpeningPosition(page);
});

test("opens at phone width with whole controls and the newest memory in view", async ({
  page,
}) => {
  // given
  await page.setViewportSize({ width: 412, height: 915 });

  // when
  await open(page);

  // then
  await expectOpeningPosition(page);
});

/** Records the row's top at the very commit that grows the list, before any scroll can follow. */
async function watchTopAtGrowth(page: Page, at: number) {
  await page.evaluate((rowAtMs) => {
    const row = () => document.querySelector(`li[data-at="${rowAtMs}"]`)!;
    const list = row().parentElement!;
    const count = list.children.length;
    const observer = new MutationObserver(() => {
      if (list.children.length === count) return;
      observer.disconnect();
      (globalThis as { topAtGrowth?: number }).topAtGrowth =
        row().getBoundingClientRect().top;
    });
    observer.observe(list, { childList: true });
  }, at);
}

test("scrubbing to the oldest memory loads the older page without a jump", async ({
  page,
}) => {
  // given
  let release = () => {};
  const held = new Promise<void>((resolve) => {
    release = resolve;
  });
  const stubs = await open(page, { holdSecondPage: held });
  const anchor = rowAt(page, newest.entry.scheduledAt);
  await expect(anchor).toBeInViewport();
  const before = await topOf(anchor);
  await watchTopAtGrowth(page, Date.parse(newest.entry.scheduledAt));

  // when
  // The window opens at the first session, so the track's very start is the oldest memory.
  await pressLensAt(page, 0.001);
  release();

  // then
  const oldestRow = rowAt(page, oldest.entry.scheduledAt);
  await expect(oldestRow).toBeAttached();
  expect(stubs.memoryRequests.some((query) => query.includes("before="))).toBe(
    true,
  );
  const atGrowth = await page.evaluate(
    () => (globalThis as { topAtGrowth?: number }).topAtGrowth,
  );
  expect(Math.abs((atGrowth ?? Infinity) - before)).toBeLessThanOrEqual(2);
  await expect
    .poll(async () =>
      Math.abs((await topOf(oldestRow)) - (await readingLine(page))),
    )
    .toBeLessThanOrEqual(40);
});

test("the next session counts down, runs, and then becomes a memory above Today", async ({
  page,
}) => {
  // given
  await open(page, {}, { paused: true });
  const card = page.getByRole("link", { name: NEXT_TITLE });
  const row = page.locator("li", { has: card });
  const today = page.getByText("Today", { exact: true });

  // then
  await expect(row.getByText("Starts in 30:00")).toBeVisible();

  // when
  await page.clock.fastForward("30:00");

  // then
  await expect(row.getByText("Running now")).toBeVisible();

  // when
  await page.clock.fastForward("01:01:00");

  // then
  await expect(row.getByText("Running now")).toBeHidden();
  const precedes = await row.evaluate(
    (element, todayElement) =>
      Boolean(
        element.compareDocumentPosition(todayElement) &
        Node.DOCUMENT_POSITION_FOLLOWING,
      ),
    await today.elementHandle(),
  );
  expect(precedes).toBe(true);
});

test("holding the lens on the right edge rolls the ribbon on and loads the list ahead", async ({
  page,
}) => {
  // given a session every week for years ahead, and the first ninety days loaded
  await open(page, { weekly: true });
  const box = (await slider(page).boundingBox())!;
  const y = box.y + box.height / 2;
  const months = page.locator("[data-ribbon-months] > span");
  const firstMonth = await months.first().textContent();

  // when the reader drags the lens to the right edge and holds it there
  await page.mouse.move(box.x + box.width / 2, y);
  await page.mouse.down();
  await page.mouse.move(box.x + box.width - 2, y, { steps: 10 });

  // then the lens travels on to a session a year past the first ninety days
  await expect
    .poll(async () => Number(await slider(page).getAttribute("aria-valuenow")))
    .toBeGreaterThan(Date.parse("2027-10-16T00:00:00Z"));
  await page.mouse.up();

  // and the window rolled on with it, never widening
  await expect(months.first()).not.toHaveText(firstMonth ?? "");
  await expect(months).toHaveCount(12);
  await expect(page.getByRole("button", { name: /show more/i })).toHaveCount(0);
  await expect(
    page.getByRole("button", { name: "Back to today" }),
  ).toBeVisible();
});

test("the keyboard jumps to the oldest memory and back to Today", async ({
  page,
}) => {
  // given
  await open(page);
  const oldestRow = rowAt(page, oldest.entry.scheduledAt);
  await slider(page).focus();

  // when
  await page.keyboard.press("Home");

  // then
  await expect
    .poll(async () =>
      Math.abs((await topOf(oldestRow)) - (await readingLine(page))),
    )
    .toBeLessThanOrEqual(40);

  // when
  await page.keyboard.press("t");

  // then
  const todayLabel = page.getByText("Today", { exact: true });
  await expect
    .poll(async () =>
      Math.abs((await topOf(todayLabel)) - (await readingLine(page))),
    )
    .toBeLessThanOrEqual(40);
});

test("at phone width nothing overflows and the lens is a comfortable target", async ({
  page,
}) => {
  // given
  await page.setViewportSize({ width: 412, height: 915 });

  // when
  await open(page);

  // then
  expect(await overflowing(page)).toEqual([]);
  const lens = (await slider(page).boundingBox())!;
  expect(lens.height).toBeGreaterThanOrEqual(44);
});

test("reduced motion leaves the next card still", async ({ page }) => {
  // given
  await page.emulateMedia({ reducedMotion: "reduce" });

  // when
  await open(page);

  // then
  const card = page.getByRole("link", { name: NEXT_TITLE });
  await expect(card).toBeVisible();
  const running = await card.evaluate(
    (element) => element.getAnimations({ subtree: true }).length,
  );
  expect(running).toBe(0);
});
