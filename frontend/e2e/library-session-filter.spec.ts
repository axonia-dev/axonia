import { expect, test } from "@playwright/test";

import { paged, shellOfCommunity, SLUG, stubReads } from "./community-home";

test.use({ viewport: { width: 1726, height: 964 } });

const SESSION_COUNT = 44;

/** Created in an order unrelated to when they are held, as a community back-filling older
 *  sessions does: the ids climb while the dates jump around. */
const sessions = Array.from({ length: SESSION_COUNT }, (_, index) => {
  const id = index + 1;
  const month = ((index * 7) % 12) + 1;
  const year = 2024 + (index % 3);
  const date = `${year}-${String(month).padStart(2, "0")}-${String((index % 27) + 1).padStart(2, "0")}`;
  return {
    id,
    seriesId: null,
    communityId: 1,
    status: "HELD",
    title: null,
    displayTitle: `Session ${id}`,
    automaticTitle: "Session",
    occurrenceDate: date,
    scheduledAt: `${date}T10:00:00Z`,
    hasTime: true,
    durationMinutes: 60,
    facilitatorUserId: null,
    attendeeCount: 0,
    notes: "",
    recordingUrl: "",
    seriesTimeZone: null,
    sections: [],
    createdAt: "2026-01-01T10:00:00Z",
    updatedAt: "2026-01-01T10:00:00Z",
  };
});

const newestFirst = [...sessions]
  .sort(
    (a, b) => b.occurrenceDate.localeCompare(a.occurrenceDate) || b.id - a.id,
  )
  .map((session) => session.displayTitle);

test("the library's session filter scrolls a long list, newest session first", async ({
  page: browser,
}) => {
  // given - a library whose artifacts come from 44 sessions
  await stubReads(browser, {
    ...shellOfCommunity,
    [`/axonia/communities/${SLUG}/artifacts`]: paged([]),
    [`/axonia/communities/${SLUG}/artifacts/sessions`]: sessions.map(
      (session) => ({ sessionId: session.id, count: 1 }),
    ),
    [`/axonia/communities/${SLUG}/sessions/past`]: paged(sessions),
  });
  await browser.goto(`/communities/${SLUG}/library`);

  // when - the session filter opens
  await browser.getByRole("button", { name: /session/i }).click();
  const listbox = browser.getByRole("listbox");
  await expect(listbox.getByRole("option")).toHaveCount(SESSION_COUNT + 2);

  // then - the sessions are listed newest first
  const labels = await listbox.getByRole("option").allTextContents();
  expect(labels.slice(2).map((label) => label.split(" · ")[0])).toEqual(
    newestFirst,
  );

  // and the list ends inside the viewport, scrolling within itself to its oldest session.
  // react-aria sizes the popover's box to the viewport but does not clip it, so the options
  // must be measured against that box, not the page.
  const popover = await listbox.locator("..").boundingBox();
  const oldest = listbox.getByRole("option").last();
  await oldest.scrollIntoViewIfNeeded();
  const option = await oldest.boundingBox();
  expect(popover).not.toBeNull();
  expect(option).not.toBeNull();
  const popoverBottom = (popover?.y ?? 0) + (popover?.height ?? 0);
  expect(popoverBottom).toBeLessThanOrEqual(964);
  expect((option?.y ?? 0) + (option?.height ?? 0)).toBeLessThanOrEqual(
    popoverBottom,
  );
});
