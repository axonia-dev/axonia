import type { Page } from "@playwright/test";
import { expect, test } from "@playwright/test";

import { community, shellOfCommunity, SLUG, stubReads } from "./community-home";

const SESSION_ID = 4;
const FIRST_SLOT = "P1s";
const NEW_NAME = "S26E10 - DevTool";
const SESSION_PATH = `/axonia/sessions/${SESSION_ID}`;
const SCHEDULE_PATH = `/axonia/communities/${SLUG}/schedule`;

const inTwoDays = new Date(Date.now() + 2 * 86_400_000);
const monthParam = inTwoDays.toISOString().slice(0, 7);

const agenda = [
  {
    id: 1,
    sessionId: SESSION_ID,
    position: 0,
    sectionTypeId: null,
    sectionType: null,
    sectionTypeIcon: null,
    sectionTypeAccentColor: null,
    title: null,
    durationMinutes: 30,
    slots: [
      {
        id: 1,
        sectionId: 1,
        position: 0,
        presenterUserIds: [],
        topicId: null,
        title: FIRST_SLOT,
        notes: null,
        durationMinutes: 30,
        tags: [],
      },
    ],
  },
];

/** The session as the server names it: its own title when it has one, else its first slot. */
function named(title: string | null) {
  return {
    seriesId: null,
    title,
    displayTitle: title ?? FIRST_SLOT,
    status: "SCHEDULED",
    occurrenceDate: null,
    scheduledAt: inTwoDays.toISOString(),
    hasTime: true,
    durationMinutes: 60,
    seriesTimeZone: null,
    place: null,
    sections: agenda,
  };
}

function sessionOf(title: string | null) {
  return {
    ...named(title),
    id: SESSION_ID,
    communityId: community.id,
    automaticTitle: FIRST_SLOT,
    facilitatorUserId: null,
    attendeeCount: 0,
    notes: null,
    recordingUrl: null,
    placeOverride: null,
    seriesPlace: null,
    createdAt: "2026-05-31T10:00:00Z",
    updatedAt: "2026-05-31T10:00:00Z",
  };
}

/** A server holding one session whose title the page can set and clear. */
async function stubRenameableSession(page: Page): Promise<string[]> {
  let title: string | null = null;
  const renames: string[] = [];
  await stubReads(page, shellOfCommunity);
  await page.route(
    (url) => url.pathname === SESSION_PATH || url.pathname === SCHEDULE_PATH,
    (route) => {
      const { pathname } = new URL(route.request().url());
      if (pathname === SCHEDULE_PATH) {
        return route.fulfill({
          json: [{ ...named(title), sessionId: SESSION_ID, virtual: false }],
        });
      }
      return route.fulfill({ json: sessionOf(title) });
    },
  );
  await page.route(`**${SESSION_PATH}/title`, (route) => {
    const body = route.request().postDataJSON() as { title: string | null };
    renames.push(JSON.stringify(body));
    title = body.title;
    return route.fulfill({ json: sessionOf(title) });
  });
  return renames;
}

const heading = (page: Page) => page.getByRole("heading", { level: 2 });
const breadcrumb = (page: Page) =>
  page.getByRole("navigation", { name: "Breadcrumb" });

async function calendarNames(page: Page, name: string): Promise<void> {
  await page.goto(
    `/communities/${SLUG}/schedule?view=calendar&month=${monthParam}`,
  );
  await expect(
    page.getByRole("button").filter({ hasText: name }).first(),
  ).toBeVisible();
  await page.goBack();
}

test("an organizer renames a session in place, and clearing the name hands back the automatic one", async ({
  page,
}) => {
  // given
  const renames = await stubRenameableSession(page);
  await page.goto(`/communities/${SLUG}/sessions/${SESSION_ID}`);
  await expect(heading(page)).toHaveText(FIRST_SLOT);

  // when
  await page.getByRole("button", { name: FIRST_SLOT, exact: true }).click();
  const field = page.getByRole("textbox", { name: "Session title" });
  await expect(field).toHaveAttribute("placeholder", FIRST_SLOT);
  await field.fill(NEW_NAME);
  await field.press("Enter");

  // then
  await expect(heading(page)).toHaveText(NEW_NAME);
  await expect(breadcrumb(page)).toContainText(NEW_NAME);
  expect(renames).toEqual([JSON.stringify({ title: NEW_NAME })]);
  await calendarNames(page, NEW_NAME);

  // when
  await page.getByRole("button", { name: NEW_NAME, exact: true }).click();
  await page.getByRole("textbox", { name: "Session title" }).fill("");
  await page.getByRole("textbox", { name: "Session title" }).press("Enter");

  // then
  await expect(heading(page)).toHaveText(FIRST_SLOT);
  await expect(breadcrumb(page)).toContainText(FIRST_SLOT);
  expect(renames.at(-1)).toBe(JSON.stringify({ title: null }));
  await calendarNames(page, FIRST_SLOT);
});
